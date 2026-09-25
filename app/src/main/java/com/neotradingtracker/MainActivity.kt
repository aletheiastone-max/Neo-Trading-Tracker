package com.neotradingtracker

import android.app.*
import android.Manifest
import android.os.*
import android.net.Uri
import android.content.*
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.webkit.*
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import androidx.work.*
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.*

class MainActivity : Activity() {
    private val green=Color.rgb(35,255,132); private val gold=Color.rgb(244,196,74)
    private lateinit var body: LinearLayout
    private lateinit var results: LinearLayout
    private val watch=linkedSetOf("BTC","ETH","XRP","SOL","DOGE","LINK","AVAX")
    private val discover=linkedSetOf("BTC","ETH","XRP","SOL","DOGE","LINK","AVAX","ADA","BNB","TRX","SUI","HBAR","LTC","BCH","DOT","UNI","AAVE","NEAR","APT","ARB","OP","PEPE","SHIB")
    private val allSymbols=mutableListOf<String>()
    private fun loadAllSymbols(done:()->Unit={}){thread{try{val root=JSONObject(URL("https://api.binance.com/api/v3/exchangeInfo").readText());val arr=root.getJSONArray("symbols");val found=mutableListOf<String>();for(i in 0 until arr.length()){val o=arr.getJSONObject(i);if(o.optString("quoteAsset")=="USDT"&&o.optString("status")=="TRADING")found.add(o.optString("baseAsset"))};synchronized(allSymbols){allSymbols.clear();allSymbols.addAll(found.distinct().sorted())};runOnUiThread{done()}}catch(_:Exception){runOnUiThread{done()}}}}
    data class TokenIdentity(val network:String,val address:String)
    data class CoinIdentity(val id:String,val name:String,val symbol:String,val contracts:List<TokenIdentity>)
    private val tokenIdentities=mutableMapOf<String,CoinIdentity>()

    private fun cgGet(url:String):String {
        val conn=URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout=15000
        conn.readTimeout=15000
        conn.requestMethod="GET"
        conn.setRequestProperty("Accept","application/json")
        conn.setRequestProperty("User-Agent","NeoTradingTracker/1.0 (Android; CoinGecko market metadata)")
        conn.setRequestProperty("x-cg-demo-api-key","")
        return try {
            val code=conn.responseCode
            if(code !in 200..299) throw java.io.IOException("CoinGecko HTTP "+code)
            conn.inputStream.bufferedReader().use{it.readText()}
        } finally { conn.disconnect() }
    }

    private fun jupiterSolanaMint(symbol:String):TokenIdentity? {
        val q=java.net.URLEncoder.encode(symbol.trim(),"UTF-8")
        val urls=listOf(
            "https://lite-api.jup.ag/tokens/v2/search?query="+q,
            "https://api.jup.ag/tokens/v1/search?query="+q
        )
        for(u in urls){
            try{
                val conn=URL(u).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout=12000;conn.readTimeout=12000
                conn.setRequestProperty("Accept","application/json")
                conn.setRequestProperty("User-Agent","NeoTradingTracker/1.0")
                val code=conn.responseCode
                if(code !in 200..299){conn.disconnect();continue}
                val raw=conn.inputStream.bufferedReader().use{it.readText()};conn.disconnect()
                val arr=if(raw.trim().startsWith("[")) JSONArray(raw) else JSONObject(raw).optJSONArray("tokens") ?: JSONArray()
                var fallback:TokenIdentity?=null
                for(i in 0 until arr.length()){
                    val o=arr.optJSONObject(i) ?: continue
                    if(!o.optString("symbol").equals(symbol,true)) continue
                    val mint=listOf("id","address","mint").map{o.optString(it,"").trim()}.firstOrNull{it.length in 32..50 && it.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))}
                    if(mint!=null){
                        val candidate=TokenIdentity("solana",mint)
                        val verified=o.optBoolean("verified",false) || o.optString("tag","").contains("verified",true) || o.optString("verification","").contains("verified",true)
                        if(verified)return candidate
                        if(fallback==null)fallback=candidate
                    }
                }
                if(fallback!=null)return fallback
            }catch(_:Exception){}
        }
        return null
    }

    private fun resolveTokenIdentity(symbol:String,done:(CoinIdentity?)->Unit){
        val normalized=symbol.trim().uppercase().removeSuffix("USDT")
        synchronized(tokenIdentities){tokenIdentities[normalized]}?.let{done(it);return}
        thread {
            try {
                val q=java.net.URLEncoder.encode(normalized,"UTF-8")
                val coins=JSONObject(cgGet("https://api.coingecko.com/api/v3/search?query="+q)).getJSONArray("coins")
                val candidates=mutableListOf<JSONObject>()
                for(i in 0 until coins.length()){
                    val o=coins.getJSONObject(i)
                    if(o.optString("symbol").equals(normalized,true)) candidates.add(o)
                }
                if(candidates.isEmpty()){runOnUiThread{done(null)};return@thread}
                var chosenCoin:JSONObject?=null
                // Symbols are not unique. Inspect exact-symbol CoinGecko candidates and prefer one
                // whose verified coin record explicitly lists a Solana mint.
                for(candidate in candidates.take(8)){
                    val candidateId=candidate.optString("id")
                    val detail=JSONObject(cgGet("https://api.coingecko.com/api/v3/coins/"+java.net.URLEncoder.encode(candidateId,"UTF-8")+"?localization=false&tickers=false&market_data=false&community_data=false&developer_data=false&sparkline=false"))
                    val p=detail.optJSONObject("platforms")
                    if(chosenCoin==null) chosenCoin=detail
                    if(p!=null && p.optString("solana","").trim().isNotBlank()){chosenCoin=detail;break}
                }
                val coin=chosenCoin ?: run{runOnUiThread{done(null)};return@thread}
                val id=coin.optString("id")
                val platforms=coin.optJSONObject("platforms")
                val contracts=mutableListOf<TokenIdentity>()
                if(platforms!=null){
                    val keys=platforms.keys()
                    while(keys.hasNext()){
                        val net=keys.next()
                        val address=platforms.optString(net,"").trim()
                        if(address.isNotBlank() && !address.equals("null",true)) contracts.add(TokenIdentity(net,address))
                    }
                }
                val cgContracts=contracts.distinctBy{it.network+"|"+it.address}.toMutableList()
                // Jupiter is Solana-native and is used as the primary/fallback source for a Solana mint.
                // Replace a CoinGecko Solana entry when Jupiter resolves the same ticker to a mint.
                val jup=jupiterSolanaMint(normalized)
                if(jup!=null){
                    cgContracts.removeAll{it.network.equals("solana",true)}
                    cgContracts.add(0,jup)
                }
                val identity=CoinIdentity(id,coin.optString("name",normalized),coin.optString("symbol",normalized),cgContracts)
                synchronized(tokenIdentities){tokenIdentities[normalized]=identity}
                runOnUiThread{done(identity)}
            }catch(e:Exception){
                val jup=jupiterSolanaMint(normalized)
                if(jup!=null){
                    val identity=CoinIdentity("jupiter-"+normalized,normalized,normalized,listOf(jup))
                    synchronized(tokenIdentities){tokenIdentities[normalized]=identity}
                    runOnUiThread{done(identity)}
                }else runOnUiThread{done(null)}
            }
        }
    }

    private fun addTokenIdentity(box:LinearLayout,c:String){
        box.addView(sectionLabel("COINGECKO TOKEN ADDRESSES // COPY FOR PURCHASE"))
        val status=t("CONNECTING TO COINGECKO...",11f,gold)
        box.addView(status)
        val addressBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        box.addView(addressBox)
        resolveTokenIdentity(c){identity->
            addressBox.removeAllViews()
            if(identity==null){
                status.text="COINGECKO ADDRESS DATA UNAVAILABLE // TAP RETRY"
                status.setTextColor(Color.rgb(255,90,70))
                status.setOnClickListener{addTokenIdentity(addressBox,c)}
            }else{
                status.setOnClickListener(null)
                status.text="COINGECKO // "+identity.name.uppercase()+" ("+identity.symbol.uppercase()+") // "+identity.contracts.size+" CONTRACT"+if(identity.contracts.size==1)"" else "S"
                status.setTextColor(green)
                if(identity.contracts.isEmpty()){
                    addressBox.addView(t("NATIVE ASSET // NO TOKEN CONTRACT ADDRESS LISTED BY COINGECKO\nUse the native "+identity.name+" network when purchasing or withdrawing. No address is invented.",12f,Color.LTGRAY))
                }else{
                    val ordered=identity.contracts.sortedWith(compareBy<TokenIdentity>({ if(it.network.equals("solana",true)) 0 else 1 },{it.network}))
                    val sol=ordered.firstOrNull{it.network.equals("solana",true)}
                    if(sol!=null){
                        val solCard=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,14,12);background=panel(gold)}
                        solCard.addView(t("★ SOLANA TOKEN ADDRESS // PRIORITY",13f,gold))
                        solCard.addView(t(sol.address,13f,Color.WHITE).apply{setTextIsSelectable(true)})
                        solCard.addView(neoButton("COPY SOL ADDRESS",gold){
                            val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(c+" Solana token address",sol.address))
                            Toast.makeText(this,c+" SOL address copied",Toast.LENGTH_SHORT).show()
                        })
                        solCard.addView(neoButton("BUY WITH SOL // JUPITER",green){
                            // Pass the CoinGecko-listed Solana mint as output token; Jupiter handles wallet connection and quoting.
                            openTradeLink("https://jup.ag/swap/SOL-"+java.net.URLEncoder.encode(sol.address,"UTF-8"),"Jupiter")
                        })
                        addressBox.addView(solCard,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,9)})
                    }else{
                        addressBox.addView(t("SOLANA // NO SOLANA CONTRACT LISTED BY COINGECKO FOR THIS ASSET",11f,Color.LTGRAY))
                    }
                    ordered.filterNot{it.network.equals("solana",true)}.forEach { token ->
                        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,10,12,10);background=panel(green)}
                        card.addView(t("NETWORK // "+token.network.uppercase(),11f,gold))
                        card.addView(t(token.address,12f,Color.WHITE).apply{setTextIsSelectable(true)})
                        card.addView(neoButton("COPY "+token.network.uppercase()+" ADDRESS",green){
                            val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(c+" "+token.network+" token address",token.address))
                            Toast.makeText(this,c+" "+token.network+" address copied",Toast.LENGTH_SHORT).show()
                        })
                        addressBox.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)})
                    }
                    addressBox.addView(t("SOLANA MINT SOURCE // JUPITER WHEN AVAILABLE; COINGECKO FALLBACK. VERIFY THE MINT IN THE SWAP/WALLET BEFORE SENDING FUNDS.",10f,gold))
                }
            }
        }
    }
    private val radarPoints=mutableListOf<RadarPoint>()
    data class RadarPoint(val symbol:String,val change:Double,val position:Double,val signal:String)

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),44);createChannel();loadWatchlist();loadAllSymbols();boot()}
    private fun loadWatchlist(){ val saved=getSharedPreferences("neo_watch",MODE_PRIVATE).getStringSet("symbols",null); if(saved!=null){watch.clear();watch.addAll(saved.sorted())} }
    private fun saveWatchlist(){getSharedPreferences("neo_watch",MODE_PRIVATE).edit().putStringSet("symbols",watch.toSet()).apply()}
    private fun addCoin(symbol:String,onDone:(Boolean)->Unit){val q=symbol.trim().uppercase().removeSuffix("USDT");if(!q.matches(Regex("[A-Z0-9]{2,15}"))){onDone(false);return};thread{try{val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/price?symbol="+q+"USDT").readText());j.getDouble("price");watch.add(q);saveWatchlist();runOnUiThread{onDone(true)}}catch(_:Exception){runOnUiThread{onDone(false)}}}}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("neo_alerts","Neo Price Alerts",NotificationManager.IMPORTANCE_HIGH))}}
    fun t(s:String,sz:Float=16f,c:Int=green)=TextView(this).apply{text=s;textSize=sz;setTextColor(c);typeface=Typeface.MONOSPACE;setPadding(12,10,12,10)}
    private fun panel(stroke:Int=green,fill:Int=Color.argb(235,1,12,8))=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(248,1,18,11),fill,Color.argb(245,0,5,3))).apply{cornerRadius=12f;setStroke(2,stroke)}
    private fun neoButton(label:String,accent:Int=green,onClick:()->Unit)=TextView(this).apply{text=label;gravity=Gravity.CENTER;textSize=15f;setTextColor(accent);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);setPadding(18,12,18,12);minHeight=52;background=panel(accent,Color.argb(245,0,9,6));setOnClickListener{animate().alpha(.65f).scaleX(.97f).scaleY(.97f).setDuration(60).withEndAction{animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();onClick()}.start()}}
    private fun spacer(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}

    private fun boot(){
        val f=FrameLayout(this);f.setBackgroundColor(Color.rgb(1,8,5));f.addView(MatrixView(this))
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(28,28,28,28)}
        val terminal=t("",20f);box.addView(t("NEO TRADING TRACKER",28f,gold));box.addView(terminal);f.addView(box);setContentView(f)
        val lines=listOf("> SYSTEM ACCESS...","You've hacked it Neo.","Knock knock.","SECURE LINE // CONNECTED")
        var i=0;val h=Handler(mainLooper);val r=object:Runnable{override fun run(){if(i<lines.size){terminal.append(lines[i++]+System.lineSeparator());h.postDelayed(this,700)}else h.postDelayed({payphone()},700)}};h.post(r)
    }

    private fun payphone(){
        val f=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)};f.addView(MatrixView(this))
        val phone=PayphoneView(this).apply{alpha=0f;scaleX=.58f;scaleY=.58f;rotationY=-10f};f.addView(phone,FrameLayout.LayoutParams(-1,-1));setContentView(f)
        phone.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f).setDuration(620).withEndAction{Handler(mainLooper).postDelayed({phone.animate().alpha(0f).scaleX(1.32f).scaleY(1.32f).setDuration(430).withEndAction{home()}.start()},1050)}.start()
    }

    private fun home(){
        val f=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)};f.addView(MatrixView(this))
        val scroll=ScrollView(this).apply{overScrollMode=View.OVER_SCROLL_NEVER};body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,30,24,90)}
        val head=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=panel(gold)}
        val titleRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};titleRow.addView(neoButton("☰",gold){menu()},LinearLayout.LayoutParams(90,-2));titleRow.addView(t("N E O\nTRADING TRACKER",23f,gold).apply{gravity=Gravity.CENTER;typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));titleRow.addView(t("●",18f,gold).apply{gravity=Gravity.CENTER;setOnClickListener{alertCenter()}},LinearLayout.LayoutParams(90,-2));head.addView(titleRow);head.addView(t("CLASSIFIED MARKET INTELLIGENCE TERMINAL",11f,Color.LTGRAY));head.addView(t("● SECURE DATA LINK // BINANCE USDT",11f,green));head.addView(t("OPERATOR: NEO     CLEARANCE: OMEGA     NODE: 01",10f,Color.rgb(120,155,135)));head.addView(t("SYSTEM // ONLINE     UPLINK // ENCRYPTED     THREAT // NOMINAL",9f,green));body.addView(head);body.addView(spacer(12));val mapPanel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,6,8,8);background=panel(gold)};mapPanel.addView(t("TACTICAL WORLD UPLINK // AZIMUTHAL DISPLAY",10f,gold));val compact=getSharedPreferences("neo_ui",MODE_PRIVATE).getBoolean("compact",true);mapPanel.addView(GleasonView(this),LinearLayout.LayoutParams(-1,if(compact)280 else 360));body.addView(mapPanel);body.addView(HudDivider(this),LinearLayout.LayoutParams(-1,18));body.addView(spacer(6))
        val search=EditText(this).apply{hint="TARGET SYMBOL // e.g. ADA";setTextColor(Color.WHITE);setHintTextColor(Color.rgb(95,130,110));textSize=15f;setPadding(20,18,20,18);background=panel(green);setSingleLine(true);maxLines=1;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH};body.addView(search);search.setOnEditorActionListener{_,action,event->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE || (event?.keyCode==android.view.KeyEvent.KEYCODE_ENTER && event.action==android.view.KeyEvent.ACTION_DOWN)){val q=search.text.toString().trim();addCoin(q){ok->if(ok){Toast.makeText(this,"Added "+q.uppercase().removeSuffix("USDT")+" to watchlist",Toast.LENGTH_SHORT).show();home()}else Toast.makeText(this,"Coin not found on Binance USDT",Toast.LENGTH_SHORT).show()};true}else false};search.setOnKeyListener{_,key,event->if(key==android.view.KeyEvent.KEYCODE_ENTER&&event.action==android.view.KeyEvent.ACTION_DOWN){val q=search.text.toString().trim();addCoin(q){ok->if(ok)home() else Toast.makeText(this,"Coin not found",Toast.LENGTH_SHORT).show()};true}else false};val addSearch=neoButton("＋ ADD SYMBOL TO WATCHLIST",gold){val q=search.text.toString();addCoin(q){ok->if(ok){Toast.makeText(this,"Added "+q.uppercase().removeSuffix("USDT"),Toast.LENGTH_SHORT).show();home()}else Toast.makeText(this,"Coin not found on Binance USDT",Toast.LENGTH_SHORT).show()}};body.addView(addSearch);body.addView(spacer(10))
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(neoButton("LIVE MARKETS",gold){markets()},LinearLayout.LayoutParams(0,-2,1f));row.addView(neoButton("◎ SCAN NOW",green){scan()},LinearLayout.LayoutParams(0,-2,1f));body.addView(row);body.addView(spacer(10));val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row2.addView(neoButton("☆ WATCHLIST",green){watchlist()},LinearLayout.LayoutParams(0,-2,1f));row2.addView(neoButton("♢ ALERTS",gold){alertCenter()},LinearLayout.LayoutParams(0,-2,1f));body.addView(row2);body.addView(spacer(14))
        val mode=getSharedPreferences("neo_alerts",MODE_PRIVATE).getBoolean("background_mode",false);val modeBox=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(14,8,14,8);background=panel(if(mode)green else gold)};val modeText=t(if(mode)"MONITOR // BACKGROUND ACTIVE" else "MONITOR // MANUAL SCAN",12f,if(mode)green else gold);val sw=Switch(this).apply{isChecked=mode;setOnCheckedChangeListener{_,on->getSharedPreferences("neo_alerts",MODE_PRIVATE).edit().putBoolean("background_mode",on).apply();setBackgroundMonitoring(on);modeText.text=if(on)"MONITOR // BACKGROUND ACTIVE" else "MONITOR // MANUAL SCAN"}};modeBox.addView(modeText,LinearLayout.LayoutParams(0,-2,1f));modeBox.addView(sw);body.addView(modeBox);body.addView(spacer(10));val telemetry=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};telemetry.addView(t("05\nTARGETS",11f,gold).apply{gravity=Gravity.CENTER;background=panel(gold)},LinearLayout.LayoutParams(0,-2,1f));telemetry.addView(t("LIVE\nUPLINK",11f,green).apply{gravity=Gravity.CENTER;background=panel(green)},LinearLayout.LayoutParams(0,-2,1f));telemetry.addView(t("24H\nVECTOR",11f,gold).apply{gravity=Gravity.CENTER;background=panel(gold)},LinearLayout.LayoutParams(0,-2,1f));body.addView(telemetry);body.addView(spacer(14))
        val rp=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,14,14);background=panel(gold)};rp.addView(t("TACTICAL MARKET RADAR // LIVE SWEEP",12f,gold));rp.addView(t("SIGNAL ACQUISITION    RANGE: 24H    ENCRYPTION: ACTIVE",9f,Color.rgb(110,145,125)));rp.addView(RadarView(this){synchronized(radarPoints){radarPoints.toList()}},LinearLayout.LayoutParams(-1,if(compact)300 else 420));body.addView(rp);body.addView(spacer(14));body.addView(t("TARGET INTELLIGENCE // PRIORITY FEED",13f,gold));results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(results);body.addView(spacer(12));body.addView(sectionLabel("WATCHLIST // QUICK ACCESS"));watch.take(8).forEach{coin->body.addView(neoButton("☆  "+coin+" / USDT",green){thread{try{val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol="+coin+"USDT").readText());val p=j.getDouble("lastPrice");val ch=j.getDouble("priceChangePercent");runOnUiThread{detail(coin,p,ch,"VIEW",p)}}catch(_:Exception){}}})};body.addView(spacer(12));body.addView(neoButton("⇄ QUICK BUY / SELL",gold){tradeHub()});body.addView(spacer(8));body.addView(neoButton("⚡ BUY WITH SANJI",green){openSanji()});body.addView(spacer(12));body.addView(bottomNav());scroll.addView(body);f.addView(scroll);setContentView(f)
    }

    private fun scanner(){
        val box=screen("MARKET SCANNER")
        box.addView(sectionLabel("TACTICAL RADAR"))
        box.addView(t("Press SCAN NOW to refresh your watchlist using live 24h market data.",11f,Color.LTGRAY))
        box.addView(RadarView(this){ synchronized(radarPoints){ radarPoints.toList() } },LinearLayout.LayoutParams(-1,340))
        results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        box.addView(neoButton("◎ SCAN NOW",green){scan()})
        box.addView(results)
        box.addView(bottomNav())
    }

    private fun scan(){
        results.removeAllViews()
        results.addView(t("◌ SCANNING ${watch.size} TARGETS // DECRYPTING MARKET DATA...",13f,gold))
        watch.forEach{coin->thread{try{
            val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol=${coin}USDT").readText())
            val p=j.getDouble("lastPrice");val low=j.getDouble("lowPrice");val high=j.getDouble("highPrice");val change=j.getDouble("priceChangePercent")
            val range=(high-low).coerceAtLeast(p*.001);val pos=(p-low)/range
            val signal=when{change>1.0&&pos<.82->"ENTER";change< -3.0||pos>.94->"NO ACTION";else->"WAIT"}
            val bid=when(signal){"ENTER"->p-range*.08;"WAIT"->p-range*.16;else->low+range*.25}
            runOnUiThread{checkAlert(coin,p);synchronized(radarPoints){radarPoints.removeAll{it.symbol==coin};radarPoints.add(RadarPoint(coin,change,pos,signal))};coinCard(coin,p,change,signal,bid)}
        }catch(e:Exception){runOnUiThread{results.addView(t("$coin  // DATA LINK UNAVAILABLE",14f,Color.LTGRAY))}}}}
    }

    private fun checkAlert(coin:String,price:Double){
        val prefs=getSharedPreferences("neo",MODE_PRIVATE);val target=prefs.getFloat("alert_"+coin,Float.NaN)
        if(!target.isNaN() && price>=target.toDouble()){Toast.makeText(this,coin+" alert reached: $"+fmt(price),Toast.LENGTH_LONG).show()}
    }

    data class BacktestResult(val wins:Int,val losses:Int,val unresolved:Int,val periodDays:Int){
        val sample:Int get()=wins+losses
        val winRate:Double get()=if(sample==0)0.0 else wins*100.0/sample
    }
    private fun backtest(symbol:String,done:(BacktestResult?)->Unit){
        thread {
            try {
                val end=System.currentTimeMillis()
                val start=end-90L*24L*60L*60L*1000L
                val url="https://api.binance.com/api/v3/klines?symbol="+symbol+"USDT&interval=4h&startTime="+start+"&endTime="+end+"&limit=1000"
                val a=JSONArray(URL(url).readText())
                data class K(val h:Double,val l:Double,val c:Double)
                val k=mutableListOf<K>()
                for(i in 0 until a.length()){val x=a.getJSONArray(i);k.add(K(x.getString(2).toDouble(),x.getString(3).toDouble(),x.getString(4).toDouble()))}
                var wins=0;var losses=0;var unresolved=0
                val fee=.001
                for(i in 6 until k.size-6){
                    val prev=k[i-6].c
                    val price=k[i].c
                    val change=(price-prev)/prev*100.0
                    var low=Double.MAX_VALUE;var high=-Double.MAX_VALUE
                    for(j in i-5..i){low=min(low,k[j].l);high=max(high,k[j].h)}
                    val range=(high-low).coerceAtLeast(price*.001)
                    val pos=(price-low)/range
                    val signal=when{change>1.0&&pos<.82->"ENTER";change< -3.0||pos>.94->"NO ACTION";else->"WAIT"}
                    if(signal!="ENTER")continue
                    val entry=price*(1.0+fee)
                    val target=entry*1.015
                    val stop=entry*.975
                    var outcome=0
                    for(j in i+1..min(i+6,k.lastIndex)){
                        val hitStop=k[j].l<=stop
                        val hitTarget=k[j].h>=target*(1.0+fee)
                        if(hitStop&&hitTarget){outcome=-1;break}
                        if(hitTarget){outcome=1;break}
                        if(hitStop){outcome=-1;break}
                    }
                    when(outcome){1->wins++;-1->losses++;else->unresolved++}
                }
                runOnUiThread{done(BacktestResult(wins,losses,unresolved,90))}
            }catch(_:Exception){runOnUiThread{done(null)}}
        }
    }

    private fun coinCard(c:String,p:Double,ch:Double,s:String,bid:Double){
        val accent=if(s=="ENTER")green else if(s=="NO ACTION")Color.rgb(255,75,55) else gold;val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=panel(accent)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};top.addView(t(c+" // USDT",20f,gold).apply{typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));top.addView(t(" "+s+" ",13f,accent).apply{gravity=Gravity.CENTER;background=panel(accent)});card.addView(top)
        card.addView(t("$"+fmt(p),28f,Color.WHITE).apply{typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)});card.addView(t("24H VECTOR  "+(if(ch>=0)"▲ " else "▼ ")+"%.2f".format(abs(ch))+"%   //   IDEAL BID  $"+fmt(bid),13f,if(ch>=0)green else Color.rgb(255,90,70)));card.addView(t("ANALYSIS // "+s+"     LIVE RULE SIGNAL",11f,accent));val bt=t("BACKTEST // CALCULATING 90D HISTORY...",11f,Color.LTGRAY);card.addView(bt);backtest(c){r->bt.text=if(r==null)"BACKTEST // DATA UNAVAILABLE" else if(r.sample==0)"BACKTEST // NO HISTORICAL ENTER SAMPLES IN 90D" else "BACKTEST // "+r.wins+"/"+r.sample+" WINS · "+"%.1f".format(r.winRate)+"% · SAMPLE "+r.sample+" · 90D · 4H CANDLES";bt.setTextColor(if(r!=null&&r.sample>0)green else Color.LTGRAY)};val meter=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=((abs(ch)*10)+35).toInt().coerceIn(35,95);progressTintList=android.content.res.ColorStateList.valueOf(accent);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(20,45,32))};card.addView(meter,LinearLayout.LayoutParams(-1,8).apply{setMargins(12,2,12,2)});card.addView(SparkView(this,ch),LinearLayout.LayoutParams(-1,90))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};actions.addView(neoButton("COIN DETAIL",green){detail(c,p,ch,s,bid)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(neoButton("ARM ALERT",gold){alertDialog(c,p)},LinearLayout.LayoutParams(0,-2,1f));card.addView(actions);results.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,8)})
    }

    private fun sectionLabel(s:String)=t("▰  "+s.uppercase()+"  //",11f,gold).apply{letterSpacing=.12f}
    private fun bottomNav():LinearLayout{
        val n=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;background=panel(gold);setPadding(4,5,4,5)}
        listOf("⌂\nHOME","◎\nSCAN","▥\nMARKETS","☆\nWATCH","♢\nALERTS").forEach{v->val b=t(v,9f,if(v.contains("HOME"))gold else green).apply{gravity=Gravity.CENTER;setOnClickListener{when{v.contains("HOME")->home();v.contains("SCAN")->scanner();v.contains("MARKETS")->markets();v.contains("WATCH")->watchlist();else->alertCenter()}}};n.addView(b,LinearLayout.LayoutParams(0,62,1f))}
        return n
    }
    private fun screen(title:String):LinearLayout{
        val f=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)};if(getSharedPreferences("neo_ui",MODE_PRIVATE).getBoolean("animations",true))f.addView(MatrixView(this));val sc=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,30,24,80)}
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};nav.addView(neoButton("‹ BACK",gold){home()},LinearLayout.LayoutParams(0,-2,.35f));nav.addView(t(title,21f,Color.WHITE).apply{gravity=Gravity.CENTER;typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));box.addView(nav);box.addView(spacer(16));sc.addView(box);f.addView(sc);setContentView(f);return box
    }
    private fun settings(){
        val box=screen("SETTINGS");box.addView(sectionLabel("SYSTEM SETTINGS"));val ui=getSharedPreferences("neo_ui",MODE_PRIVATE);val alerts=getSharedPreferences("neo_alerts",MODE_PRIVATE)
        val compact=Switch(this).apply{text="Compact dashboard";setTextColor(Color.WHITE);isChecked=ui.getBoolean("compact",true);setOnCheckedChangeListener{_,v->ui.edit().putBoolean("compact",v).apply()}};box.addView(compact)
        val bg=Switch(this).apply{text="Background price monitoring";setTextColor(Color.WHITE);isChecked=alerts.getBoolean("background_mode",false);setOnCheckedChangeListener{_,v->alerts.edit().putBoolean("background_mode",v).apply();setBackgroundMonitoring(v)}};box.addView(bg)
        box.addView(neoButton("REFRESH COIN DIRECTORY",gold){loadAllSymbols{Toast.makeText(this,"Loaded "+allSymbols.size+" active USDT markets",Toast.LENGTH_LONG).show()}})
        box.addView(neoButton("RESET WATCHLIST",green){watch.clear();watch.addAll(listOf("BTC","ETH","XRP","SOL","DOGE","LINK","AVAX"));saveWatchlist();Toast.makeText(this,"Default watchlist restored",Toast.LENGTH_SHORT).show()})
        box.addView(t("Market source // Binance public USDT markets\nBackground checks // Android minimum 15 minutes\nWatchlist // persistent on device",12f,green));box.addView(bottomNav())
    }
    private fun appearance(){
        val box=screen("APPEARANCE");box.addView(sectionLabel("DISPLAY"));val prefs=getSharedPreferences("neo_ui",MODE_PRIVATE)
        val anim=Switch(this).apply{text="Matrix background animations";setTextColor(Color.WHITE);isChecked=prefs.getBoolean("animations",true);setOnCheckedChangeListener{_,v->prefs.edit().putBoolean("animations",v).apply();Toast.makeText(this@MainActivity,"Animation setting saved",Toast.LENGTH_SHORT).show()}};box.addView(anim)
        val compact=Switch(this).apply{text="Compact tactical layout";setTextColor(Color.WHITE);isChecked=prefs.getBoolean("compact",true);setOnCheckedChangeListener{_,v->prefs.edit().putBoolean("compact",v).apply()}};box.addView(compact)
        box.addView(t("Theme // NEO MATRIX\nPalette // BLACK + NEON GREEN + GOLD\nTypography // TERMINAL MONOSPACE",12f,gold));box.addView(neoButton("PREVIEW DASHBOARD",green){home()});box.addView(bottomNav())
    }
    private fun connection(){
        val box=screen("CONNECTION");box.addView(sectionLabel("DATA UPLINK"));val status=t("MARKET DATA // TEST NOT RUN",12f,gold);box.addView(status)
        box.addView(neoButton("TEST BINANCE MARKET UPLINK",gold){thread{try{val j=JSONObject(URL("https://api.binance.com/api/v3/time").readText());runOnUiThread{status.text="MARKET DATA // ONLINE\nSERVER TIME // "+j.optLong("serverTime");status.setTextColor(green)}}catch(e:Exception){runOnUiThread{status.text="MARKET DATA // OFFLINE\n"+(e.message?:"Connection failed");status.setTextColor(Color.RED)}}}})
        box.addView(neoButton("RELOAD ALL COINS",green){loadAllSymbols{status.text="COIN DIRECTORY // "+allSymbols.size+" ACTIVE USDT MARKETS";status.setTextColor(if(allSymbols.isNotEmpty())green else Color.RED)}})
        box.addView(t("SANJI / BYBIT / OKX / COINBASE are external authenticated trade destinations. Neo does not store exchange passwords, API secrets or private keys.",11f,Color.LTGRAY));box.addView(bottomNav())
    }
    private fun tradeLog(){
        val box=screen("TRADE LOG");box.addView(sectionLabel("OPERATOR LOG"));val prefs=getSharedPreferences("neo_trade_log",MODE_PRIVATE);val log=prefs.getString("notes","")?:""
        val note=EditText(this).apply{hint="Add a trade note, e.g. XRP buy 0.52";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setSingleLine(false);minLines=3;background=panel(green);setPadding(16,14,16,14)};box.addView(note)
        box.addView(neoButton("SAVE LOG ENTRY",gold){val q=note.text.toString().trim();if(q.isNotEmpty()){val stamp=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(java.util.Date());prefs.edit().putString("notes","["+stamp+"] "+q+"\n"+(prefs.getString("notes","")?:"")).apply();tradeLog()}})
        box.addView(t(if(log.isBlank())"NO LOCAL TRADE NOTES YET" else log,12f,if(log.isBlank())Color.LTGRAY else green));box.addView(t("Orders made in external exchanges remain in that provider's official order history.",10f,Color.LTGRAY));box.addView(bottomNav())
    }
    private fun help(){
        val box=screen("HELP");box.addView(sectionLabel("QUICK GUIDE"));box.addView(t("SEARCH // tap the field and type any active Binance USDT base symbol or part of its name. Results update while you type. Use Search/Add to follow it.\n\nWATCHLIST // tap for chart; long-press a market row to add/remove.\n\nRADAR // populated by SCAN NOW. Green=ENTER, gold=WAIT, red=NO ACTION. Distance reflects current position in the 24h range.\n\nALERTS // saved locally; background monitoring can check them approximately every 15 minutes.\n\nTRADE // Sanji and exchange buttons open their authenticated environments. Neo itself does not submit exchange orders.",12f,Color.WHITE));box.addView(bottomNav())
    }
    private fun markets(){
        val box=screen("LIVE MARKETS");val search=EditText(this).apply{hint="Search any Binance USDT coin...";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setPadding(18,16,18,16);background=panel(green);setSingleLine(true);maxLines=1;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH};box.addView(search);box.addView(spacer(8));box.addView(neoButton("SEARCH + FOLLOW COIN",gold){val q=search.text.toString().trim();addCoin(q){ok->if(ok){Toast.makeText(this,"Following "+q.uppercase().removeSuffix("USDT"),Toast.LENGTH_SHORT).show();loadAllSymbols{}}else Toast.makeText(this,"No active Binance USDT market found",Toast.LENGTH_LONG).show()}});box.addView(spacer(10))
        box.addView(t("Type a symbol then press Enter to add it. Long-press any listed coin to add/remove it from your watchlist.",11f,Color.LTGRAY));box.addView(sectionLabel("MARKET UPLINK"));box.addView(t("ALL     FAVOURITES     GAINERS     LOSERS",12f,gold));box.addView(HudDivider(this),LinearLayout.LayoutParams(-1,14))
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};box.addView(list)
        fun loadMarket(filter:String=""){list.removeAllViews();(if(filter.isBlank()) watch.toList() else synchronized(allSymbols){allSymbols.filter{it.contains(filter.uppercase())}.take(40)}).forEach { coin ->
            thread {
                try {
                    val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol="+coin+"USDT").readText())
                    val p=j.getDouble("lastPrice"); val ch=j.getDouble("priceChangePercent")
                    runOnUiThread {
                        val r=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; background=panel(if(ch>=0)green else Color.rgb(255,75,55)); setPadding(12,8,12,8) }
                        r.addView(t(coin,17f,gold),LinearLayout.LayoutParams(0,-2,.45f))
                        r.addView(t("$"+fmt(p),14f,Color.WHITE),LinearLayout.LayoutParams(0,-2,.75f))
                        r.addView(t((if(ch>=0)"+ " else "")+"%.2f".format(ch)+"%",13f,if(ch>=0)green else Color.rgb(255,75,55)),LinearLayout.LayoutParams(0,-2,.55f))
                        r.setOnClickListener { chart(coin) }; r.setOnLongClickListener{ if(watch.contains(coin)){watch.remove(coin);saveWatchlist();Toast.makeText(this,"Removed "+coin+" from watchlist",Toast.LENGTH_SHORT).show()}else{watch.add(coin);saveWatchlist();Toast.makeText(this,"Added "+coin+" to watchlist",Toast.LENGTH_SHORT).show()};loadMarket(search.text.toString());true }
                        list.addView(r,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)})
                    }
                } catch (_:Exception) { }
            }
        }}
        search.isFocusableInTouchMode=true;search.isClickable=true;search.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;search.setSingleLine(true);search.imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,d:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,d:Int){val q=s?.toString()?.trim()?:"";if(q.isBlank())loadMarket() else if(allSymbols.isEmpty())loadAllSymbols{loadMarket(q)} else loadMarket(q)};override fun afterTextChanged(e:Editable?) {}})
        search.setOnEditorActionListener{_,_,_->val q=search.text.toString().trim().uppercase().removeSuffix("USDT");if(q.matches(Regex("[A-Z0-9]{2,15}"))){addCoin(q){ok->if(ok)loadMarket(q) else Toast.makeText(this,"Coin not found on Binance USDT",Toast.LENGTH_SHORT).show()}};true}
        loadMarket()
        box.addView(spacer(12));box.addView(bottomNav())
    }
    private fun openTradeLink(url:String,name:String){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){Toast.makeText(this,"Unable to open "+name,Toast.LENGTH_LONG).show()}}
    private fun openSanji(){val tg=Uri.parse("https://t.me/SanjiTradingBot");openTradeLink(tg.toString(),"Sanji")}
    private fun tradeHub(){val box=screen("QUICK TRADE");box.addView(sectionLabel("EXECUTION DESK"));box.addView(neoButton("⚡ SANJI // BUY & SELL",green){openSanji()});box.addView(spacer(8));box.addView(sectionLabel("QUICK EXCHANGE LINKS"));box.addView(neoButton("BYBIT // TRADE",gold){openTradeLink("https://www.bybit.com/en/trade/spot/","Bybit")});box.addView(neoButton("OKX // BUY CRYPTO",green){openTradeLink("https://www.okx.com/buy-crypto","OKX")});box.addView(neoButton("COINBASE ADVANCED // TRADE",gold){openTradeLink("https://www.coinbase.com/advanced-trade","Coinbase")});box.addView(spacer(10));box.addView(t("Connect an exchange before live orders are enabled. API secrets are never hard-coded into the app.",12f,Color.LTGRAY));box.addView(spacer(10));box.addView(neoButton("BINANCE SPOT // CONNECT",gold){Toast.makeText(this,"Secure exchange connection setup required",Toast.LENGTH_LONG).show()});box.addView(spacer(8));box.addView(t("BUY / SELL panel will unlock after authenticated exchange connection. Market, limit, quantity, estimated total and final confirmation will be shown before every order.",12f,green));box.addView(spacer(12));box.addView(bottomNav())}
    private fun startMinuteBanner(symbol:String,banner:TextView){
        val handler=Handler(mainLooper)
        var previous:Double?=null
        val task=object:Runnable{
            override fun run(){
                thread{
                    try{
                        val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/price?symbol="+symbol+"USDT").readText())
                        val price=j.getDouble("price")
                        runOnUiThread{
                            if(!banner.isAttachedToWindow)return@runOnUiThread
                            val before=previous
                            if(before==null){
                                banner.text="1 MINUTE MOVE // BASELINE $"+fmt(price)+" // NEXT UPDATE 60S"
                                banner.setTextColor(gold);banner.background=panel(gold)
                            }else{
                                val move=(price-before)/before*100.0
                                val up=move>=0.0
                                val accent=if(up)green else Color.rgb(255,75,55)
                                banner.text=(if(up)"▲ " else "▼ ")+"1 MINUTE // "+(if(up)"+" else "")+"%.3f".format(move)+"%   //   $"+fmt(price)
                                banner.setTextColor(accent);banner.background=panel(accent)
                                banner.animate().alpha(.35f).scaleX(.985f).setDuration(120).withEndAction{banner.animate().alpha(1f).scaleX(1f).setDuration(280).start()}.start()
                            }
                            previous=price
                            handler.postDelayed(this,60000)
                        }
                    }catch(_:Exception){runOnUiThread{if(banner.isAttachedToWindow){banner.text="1 MINUTE MOVE // DATA LINK RETRYING";banner.setTextColor(Color.LTGRAY);handler.postDelayed(this,60000)}}}
                }
            }
        }
        handler.post(task)
    }

    private fun detail(c:String,p:Double,ch:Double,s:String,bid:Double){
        val box=screen(c+" / USDT");box.addView(t("$"+fmt(p),29f,Color.WHITE));box.addView(t((if(ch>=0)"▲ +" else "▼ ")+"%.2f".format(ch)+"% (24h)",14f,if(ch>=0)green else Color.RED));val minuteBanner=t("1 MINUTE MOVE // LOADING...",16f,gold).apply{gravity=Gravity.CENTER;background=panel(gold);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)};box.addView(minuteBanner);startMinuteBanner(c,minuteBanner);box.addView(spacer(12));addTokenIdentity(box,c);box.addView(spacer(12))
        val intelligence=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,14,16,14);background=panel(gold)};intelligence.addView(t("AI ACTION     "+s,16f,gold));intelligence.addView(t("◎ IDEAL BID     $"+fmt(bid),14f,green));intelligence.addView(t("◉ TARGET 1      $"+fmt(p*1.015),14f));intelligence.addView(t("◉ TARGET 2      $"+fmt(p*1.035),14f));intelligence.addView(t("◇ STOP LOSS     $"+fmt(p*.975),14f,Color.rgb(255,90,70)));box.addView(intelligence);val historical=t("BACKTEST // CALCULATING 90D HISTORY...",12f,gold);box.addView(historical);backtest(c){r->historical.text=if(r==null)"BACKTEST // DATA UNAVAILABLE" else if(r.sample==0)"BACKTEST // NO HISTORICAL ENTER SAMPLES IN 90D" else "BACKTEST // "+r.wins+"/"+r.sample+" WINS · "+"%.1f".format(r.winRate)+"% · SAMPLE "+r.sample+" · PERIOD 90D\nRULES // ENTER SIGNAL · +1.5% TARGET · -2.5% STOP · 0.1% FEE/LEG · 24H OUTCOME WINDOW";historical.setTextColor(if(r!=null&&r.sample>0)green else Color.LTGRAY)};box.addView(spacer(12));box.addView(neoButton("⚡ BUY / SELL WITH SANJI",gold){openSanji()});box.addView(spacer(8));box.addView(neoButton("LIVE CANDLES",green){chart(c)});box.addView(spacer(8));box.addView(neoButton("♢ SET ALERT",gold){alertDialog(c,p)});box.addView(spacer(10));box.addView(t("1m    5m    15m    1h    4h    1D",12f,green));box.addView(spacer(12));box.addView(bottomNav())
    }
    private fun alertCenter(){
        val box=screen("SET ALERT");box.addView(sectionLabel("ALERT PROTOCOL"));box.addView(t("CUSTOM PRICE & % ALERTS",13f,gold))
        box.addView(t("PRICE     % CHANGE     INDICATOR",12f,green))
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,12);background=panel(green)}
        card.addView(t("Alert me when price is",13f,Color.WHITE))
        card.addView(t("ABOVE     selected price        ●",13f,green))
        card.addView(t("BELOW     selected price        ●",13f,green))
        card.addView(t("% CHANGE (24H)",13f,Color.WHITE));card.addView(t("GREATER THAN   +5%             ●",13f,green));card.addView(t("LESS THAN      -5%             ○",13f,Color.LTGRAY))
        card.addView(t("NOTIFICATION METHOD",12f,gold));card.addView(t("◉ PUSH NOTIFICATION             ●",13f,green));card.addView(t("◇ SOUND        MATRIX TONE",13f));card.addView(t("↻ REPEAT       UNTIL CANCELLED",13f))
        box.addView(card);box.addView(spacer(12))
        watch.forEach{coin->box.addView(neoButton(coin+" / USDT  //  CREATE ALERT",green){thread{try{val p=JSONObject(URL("https://api.binance.com/api/v3/ticker/price?symbol="+coin+"USDT").readText()).getDouble("price");runOnUiThread{alertDialog(coin,p)}}catch(_:Exception){}}})}
        box.addView(spacer(12));box.addView(bottomNav())
    }

    private fun watchlist(){
        val box=screen("WATCHLIST")
        box.addView(sectionLabel("TRACKED ASSETS"))
        box.addView(neoButton("+ SEARCH / FOLLOW COIN",gold){markets()})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        box.addView(list)
        watch.toList().forEach { coin ->
            thread {
                try {
                    val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol="+coin+"USDT").readText())
                    val price=j.getDouble("lastPrice")
                    val change=j.getDouble("priceChangePercent")
                    runOnUiThread {
                        val row=LinearLayout(this).apply {
                            orientation=LinearLayout.HORIZONTAL
                            gravity=Gravity.CENTER_VERTICAL
                            background=panel(if(change>=0) green else Color.rgb(255,75,55))
                            setPadding(12,8,12,8)
                        }
                        row.addView(t("☆ "+coin,16f,gold),LinearLayout.LayoutParams(0,-2,.6f))
                        row.addView(t("$"+fmt(price),13f,Color.WHITE),LinearLayout.LayoutParams(0,-2,.7f))
                        row.addView(t((if(change>=0) "+ " else "")+"%.2f".format(change)+"%",12f,if(change>=0) green else Color.RED))
                        row.setOnClickListener{chart(coin)}
                        row.setOnLongClickListener{watch.remove(coin);saveWatchlist();watchlist();true}
                        list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,4)})
                    }
                } catch (_:Exception) {}
            }
        }
        box.addView(t("Long-press a tracked asset to remove it.",10f,Color.LTGRAY))
        box.addView(bottomNav())
    }
    private fun menu(){
        val box=screen("N E O // MENU");listOf("⌂  DASHBOARD","▥  MARKETS","◎  SCANNER","★  WATCHLIST","♢  ALERTS","▤  TRADE LOG","⚙  SETTINGS","◈  CONNECTION","◐  APPEARANCE","?  HELP").forEach{label->box.addView(neoButton(label,if(label.contains("DASHBOARD"))green else Color.LTGRAY){when{label.contains("MARKETS")->markets();label.contains("SCANNER")->scanner();label.contains("WATCHLIST")->watchlist();label.contains("ALERTS")->alertCenter();label.contains("TRADE LOG")->tradeLog();label.contains("SETTINGS")->settings();label.contains("CONNECTION")->connection();label.contains("APPEARANCE")->appearance();label.contains("HELP")->help();else->home()}},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,3,0,3)})}
    }

    private fun chart(c:String){
        val frame=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,24,18,18)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        top.addView(neoButton("‹ BACK",gold){home()},LinearLayout.LayoutParams(0,-2,.35f));top.addView(t(c+" / USDT",20f,Color.WHITE).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f));top.addView(t("★",22f,gold),LinearLayout.LayoutParams(60,-2));box.addView(top)
        box.addView(t("1m     5m     15m     1h     4h     1D",12f,green));val w=WebView(this);w.settings.javaScriptEnabled=true;w.setBackgroundColor(Color.BLACK);w.loadUrl("https://www.tradingview.com/chart/?symbol=BINANCE:"+c+"USDT");box.addView(w,LinearLayout.LayoutParams(-1,0,1f))
        val indicators=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};listOf("MA","EMA","RSI","MACD","VOL").forEach{indicators.addView(t(it,11f,green).apply{gravity=Gravity.CENTER;background=panel(green)},LinearLayout.LayoutParams(0,52,1f))};box.addView(indicators)
        val order=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8);background=panel(gold)};order.addView(t("ORDER BOOK",13f,Color.WHITE));order.addView(t("BID                 ASK",12f,gold));order.addView(t("LIVE DEPTH AVAILABLE IN CHART FEED",11f,Color.LTGRAY));box.addView(order);frame.addView(box);setContentView(frame)
    }

    private fun setBackgroundMonitoring(on:Boolean){
        val wm=WorkManager.getInstance(this)
        if(on){
            val req=PeriodicWorkRequestBuilder<AlertWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork("neo_price_alerts",ExistingPeriodicWorkPolicy.UPDATE,req)
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),44)
        }else wm.cancelUniqueWork("neo_price_alerts")
    }
    private fun alertDialog(c:String,p:Double){
        val input=EditText(this).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(fmt(p))}
        AlertDialog.Builder(this).setTitle("$c price alert").setMessage("Notify when a future scan reaches this USDT price").setView(input)
            .setPositiveButton("SAVE"){_,_->val v=input.text.toString().toDoubleOrNull();if(v!=null){val prefs=getSharedPreferences("neo_alerts",MODE_PRIVATE);val armed=(prefs.getStringSet("armed_symbols",emptySet())?:emptySet()).toMutableSet();armed.add(c);prefs.edit().putString(c,v.toString()).putString(c+"_last",p.toString()).putStringSet("armed_symbols",armed).apply();Toast.makeText(this,"Alert armed for $c at ${fmt(v)}",Toast.LENGTH_LONG).show()}}.setNegativeButton("CANCEL",null).show()
    }
    private fun fmt(v:Double)=if(v>=100)"%.2f".format(v) else if(v>=1)"%.4f".format(v) else "%.6f".format(v)

    class MatrixView(c:Context):View(c){
        val p=Paint().apply{color=Color.rgb(0,120,55);textSize=18f;typeface=Typeface.MONOSPACE};var tick=0
        override fun onDraw(x:Canvas){super.onDraw(x);x.drawColor(Color.rgb(1,7,4));val chars="01{}<>#@+";val cols=(width/34).coerceAtLeast(1);for(i in 0..cols){for(j in 0..(height/48)){val y=j*48+(tick+i*37)%48;val ch=chars[(i*7+j*3+tick)%chars.length].toString();p.alpha=35+((i+j+tick)%5)*30;x.drawText(ch,(i*34).toFloat(),y.toFloat(),p)}};tick=(tick+2)%500;postInvalidateDelayed(70)}
    }
    class RadarView(c:Context,val feed:()->List<RadarPoint>):View(c){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.5f;color=Color.rgb(0,255,119)};var a=0f
        override fun onDraw(x:Canvas){super.onDraw(x);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.43f;p.alpha=70;for(k in 1..5)x.drawCircle(cx,cy,r*k/5,p);for(d in 0 until 360 step 30){val q=Math.toRadians(d.toDouble());x.drawLine(cx,cy,cx+cos(q).toFloat()*r,cy+sin(q).toFloat()*r,p)};for(n in 0..8){val q=Math.toRadians((a-n*5).toDouble());p.alpha=(210-n*20).coerceAtLeast(25);x.drawLine(cx,cy,cx+cos(q).toFloat()*r,cy+sin(q).toFloat()*r,p)};p.style=Paint.Style.FILL;p.alpha=240;feed().forEachIndexed{idx,q->val ang=Math.toRadians((idx*137.5+q.change*9).toDouble());val rr=((.18+.68*q.position.coerceIn(0.0,1.0))*r).toFloat();p.color=when(q.signal){"ENTER"->Color.rgb(35,255,132);"NO ACTION"->Color.rgb(255,75,55);else->Color.rgb(244,196,74)};x.drawCircle(cx+cos(ang).toFloat()*rr,cy+sin(ang).toFloat()*rr,8f,p);p.textSize=18f;p.typeface=Typeface.MONOSPACE;x.drawText(q.symbol,cx+cos(ang).toFloat()*rr+10,cy+sin(ang).toFloat()*rr,p)};p.style=Paint.Style.STROKE;p.color=Color.rgb(0,255,119);a=(a+3.2f)%360;postInvalidateDelayed(28)}}
    class SparkView(c:Context,val change:Double):View(c){private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=3f;color=if(change>=0)Color.rgb(35,255,132) else Color.rgb(255,80,60)}
        override fun onDraw(x:Canvas){super.onDraw(x);val path=Path();val mid=height*.55f;for(i in 0..20){val xx=width*i/20f;val wave=(sin(i*.9+change)*height*.17).toFloat();val trend=(-change.coerceIn(-8.0,8.0)*i/20.0*height*.025).toFloat();val yy=mid+wave+trend;if(i==0)path.moveTo(xx,yy) else path.lineTo(xx,yy)};x.drawPath(path,p)}}
    class HudDivider(c:Context):View(c){private val p=Paint(Paint.ANTI_ALIAS_FLAG);override fun onDraw(x:Canvas){p.color=Color.rgb(35,255,132);p.strokeWidth=2f;p.alpha=100;x.drawLine(0f,height/2f,width.toFloat(),height/2f,p);for(i in 0..width step 28)x.drawLine(i.toFloat(),height/2f-4,i.toFloat(),height/2f+4,p)}}
    class GleasonView(c:Context):View(c){private val p=Paint(Paint.ANTI_ALIAS_FLAG);private var phase=0f
        override fun onDraw(x:Canvas){super.onDraw(x);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.43f;p.style=Paint.Style.FILL;p.color=Color.rgb(1,12,7);x.drawCircle(cx,cy,r,p);p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=Color.rgb(35,255,132);p.alpha=115;for(k in 1..6)x.drawCircle(cx,cy,r*k/6,p);for(d in 0 until 360 step 15){val q=Math.toRadians(d.toDouble());x.drawLine(cx,cy,cx+cos(q).toFloat()*r,cy+sin(q).toFloat()*r,p)}
            p.alpha=230;p.strokeWidth=3f;p.color=Color.rgb(244,196,74);val land=listOf(floatArrayOf(-.15f,-.12f,-.32f,-.28f,-.42f,-.05f,-.31f,.10f,-.18f,.20f),floatArrayOf(.10f,-.30f,.32f,-.22f,.38f,-.02f,.22f,.08f,.12f,-.06f),floatArrayOf(.05f,.16f,.28f,.20f,.35f,.36f,.12f,.42f,-.02f,.27f));for(a in land){val q=Path();q.moveTo(cx+a[0]*r,cy+a[1]*r);var i=2;while(i<a.size){q.lineTo(cx+a[i]*r,cy+a[i+1]*r);i+=2};x.drawPath(q,p)}
            p.color=Color.rgb(35,255,132);p.alpha=170;for(n in 0..4){val q=Math.toRadians((phase+n*72).toDouble());x.drawCircle(cx+cos(q).toFloat()*r*.76f,cy+sin(q).toFloat()*r*.76f,7f,p)};phase=(phase+1.2f)%360;postInvalidateDelayed(35)}
    }
    class PayphoneView(c:Context):View(c){private val g=Color.rgb(0,255,119);private val gold=Color.rgb(255,205,80);private val p=Paint(Paint.ANTI_ALIAS_FLAG);private val start=System.currentTimeMillis()
        override fun onDraw(x:Canvas){super.onDraw(x);val w=width.toFloat();val h=height.toFloat();val cx=w/2f;val cy=h*.48f;val z=((System.currentTimeMillis()-start)/1700f).coerceIn(0f,1f);p.setShadowLayer(34f,0f,0f,g);setLayerType(LAYER_TYPE_SOFTWARE,p);p.style=Paint.Style.FILL;p.color=Color.rgb(5,20,14);x.drawRoundRect(cx-w*.28f,cy-h*.25f,cx+w*.28f,cy+h*.27f,28f,28f,p);p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=g;x.drawRoundRect(cx-w*.28f,cy-h*.25f,cx+w*.28f,cy+h*.27f,28f,28f,p);p.style=Paint.Style.FILL;p.color=Color.rgb(12,35,24);x.drawRoundRect(cx-w*.19f,cy-h*.12f,cx+w*.19f,cy+h*.17f,16f,16f,p);p.color=gold;x.drawRoundRect(cx-w*.12f,cy-h*.20f,cx+w*.12f,cy-h*.15f,8f,8f,p);p.color=g;for(r in 0..3)for(col in 0..2)x.drawCircle(cx+(col-1)*w*.065f,cy+(r-1)*h*.042f,8f,p);p.style=Paint.Style.STROKE;p.strokeWidth=18f;p.color=Color.rgb(30,55,43);val path=Path();path.moveTo(cx-w*.25f,cy-h*.13f);path.cubicTo(cx-w*.36f,cy-h*.20f,cx-w*.36f,cy+h*.12f,cx-w*.24f,cy+h*.16f);x.drawPath(path,p);p.strokeWidth=5f;p.color=g;x.drawPath(path,p);p.clearShadowLayer();p.style=Paint.Style.FILL;p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textAlign=Paint.Align.CENTER;p.textSize=28f;p.color=gold;x.drawText("SECURE PAYPHONE // NODE 01",cx,cy+h*.34f,p);p.textSize=20f;p.color=g;x.drawText(if(z>.55f)"KNOCK KNOCK." else "MATERIALIZING SECURE LINK...",cx,cy+h*.39f,p);if(z>.72f){p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.alpha=((1f-z)*900).toInt().coerceIn(30,220);x.drawCircle(cx,cy,h*(z-.68f)*.55f,p)};postInvalidateDelayed(30)}}
}