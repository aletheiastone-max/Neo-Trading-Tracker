package com.neotradingtracker

import android.app.*
import android.Manifest
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.*

class MainActivity : Activity() {
    private val green=Color.rgb(35,255,132); private val gold=Color.rgb(244,196,74)
    private lateinit var body: LinearLayout
    private lateinit var results: LinearLayout
    private val watch=linkedSetOf("BTC","ETH","XRP","SOL","DOGE")

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),44);createChannel();boot()}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("neo_alerts","Neo Price Alerts",NotificationManager.IMPORTANCE_HIGH))}}
    fun t(s:String,sz:Float=16f,c:Int=green)=TextView(this).apply{text=s;textSize=sz;setTextColor(c);typeface=Typeface.MONOSPACE;setPadding(12,10,12,10)}
    private fun panel(stroke:Int=green,fill:Int=Color.argb(235,1,12,8))=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(248,1,18,11),fill,Color.argb(245,0,5,3))).apply{cornerRadius=12f;setStroke(2,stroke)}
    private fun neoButton(label:String,accent:Int=green,onClick:()->Unit)=TextView(this).apply{text=label;gravity=Gravity.CENTER;textSize=15f;setTextColor(accent);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);setPadding(18,20,18,20);background=panel(accent,Color.argb(245,0,9,6));setOnClickListener{animate().alpha(.65f).scaleX(.97f).scaleY(.97f).setDuration(60).withEndAction{animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();onClick()}.start()}}
    private fun spacer(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}

    private fun boot(){
        val f=FrameLayout(this);f.setBackgroundColor(Color.rgb(1,8,5));f.addView(MatrixView(this))
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(28,28,28,28)}
        val terminal=t("",20f);box.addView(t("NEO TRADING TRACKER",28f,gold));box.addView(terminal);f.addView(box);setContentView(f)
        val lines=listOf("> SYSTEM ACCESS...","You've hacked it Neo.","Knock knock.","☎  SECURE LINE CONNECTED")
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
        val titleRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};titleRow.addView(neoButton("☰",gold){menu()},LinearLayout.LayoutParams(90,-2));titleRow.addView(t("N E O\nTRADING TRACKER",23f,gold).apply{gravity=Gravity.CENTER;typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));titleRow.addView(t("♢",23f,gold).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(90,-2));head.addView(titleRow);head.addView(t("CLASSIFIED MARKET INTELLIGENCE TERMINAL",11f,Color.LTGRAY));head.addView(t("● SECURE DATA LINK // BINANCE USDT",11f,green));head.addView(t("OPERATOR: NEO     CLEARANCE: OMEGA     NODE: 01",10f,Color.rgb(120,155,135)));head.addView(t("SYSTEM // ONLINE     UPLINK // ENCRYPTED     THREAT // NOMINAL",9f,green));body.addView(head);body.addView(spacer(12));val mapPanel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,6,8,8);background=panel(gold)};mapPanel.addView(t("TACTICAL WORLD UPLINK // AZIMUTHAL DISPLAY",10f,gold));mapPanel.addView(GleasonView(this),LinearLayout.LayoutParams(-1,360));body.addView(mapPanel);body.addView(HudDivider(this),LinearLayout.LayoutParams(-1,18));body.addView(spacer(6))
        val search=EditText(this).apply{hint="TARGET SYMBOL // e.g. ADA";setTextColor(Color.WHITE);setHintTextColor(Color.rgb(95,130,110));textSize=15f;setPadding(20,18,20,18);background=panel(green)};body.addView(search);body.addView(spacer(10))
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(neoButton("LIVE MARKETS",gold){markets()},LinearLayout.LayoutParams(0,-2,1f));row.addView(neoButton("◎ SCAN NOW",green){scan()},LinearLayout.LayoutParams(0,-2,1f));body.addView(row);body.addView(spacer(10));val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row2.addView(neoButton("☆ WATCHLIST",green){watchlist()},LinearLayout.LayoutParams(0,-2,1f));row2.addView(neoButton("♢ ALERTS",gold){alertCenter()},LinearLayout.LayoutParams(0,-2,1f));body.addView(row2);body.addView(spacer(14))
        val mode=getSharedPreferences("neo_alerts",MODE_PRIVATE).getBoolean("background_mode",false);val modeBox=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(14,8,14,8);background=panel(if(mode)green else gold)};val modeText=t(if(mode)"MONITOR // BACKGROUND ACTIVE" else "MONITOR // MANUAL SCAN",12f,if(mode)green else gold);val sw=Switch(this).apply{isChecked=mode;setOnCheckedChangeListener{_,on->getSharedPreferences("neo_alerts",MODE_PRIVATE).edit().putBoolean("background_mode",on).apply();modeText.text=if(on)"MONITOR // BACKGROUND ACTIVE" else "MONITOR // MANUAL SCAN"}};modeBox.addView(modeText,LinearLayout.LayoutParams(0,-2,1f));modeBox.addView(sw);body.addView(modeBox);body.addView(spacer(10));val telemetry=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};telemetry.addView(t("05\nTARGETS",11f,gold).apply{gravity=Gravity.CENTER;background=panel(gold)},LinearLayout.LayoutParams(0,-2,1f));telemetry.addView(t("LIVE\nUPLINK",11f,green).apply{gravity=Gravity.CENTER;background=panel(green)},LinearLayout.LayoutParams(0,-2,1f));telemetry.addView(t("24H\nVECTOR",11f,gold).apply{gravity=Gravity.CENTER;background=panel(gold)},LinearLayout.LayoutParams(0,-2,1f));body.addView(telemetry);body.addView(spacer(14))
        val rp=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,14,14);background=panel(gold)};rp.addView(t("TACTICAL MARKET RADAR // LIVE SWEEP",12f,gold));rp.addView(t("SIGNAL ACQUISITION    RANGE: 24H    ENCRYPTION: ACTIVE",9f,Color.rgb(110,145,125)));rp.addView(RadarView(this),LinearLayout.LayoutParams(-1,500));body.addView(rp);body.addView(spacer(14));body.addView(t("TARGET INTELLIGENCE // PRIORITY FEED",13f,gold));results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(results);scroll.addView(body);f.addView(scroll);setContentView(f);scan()
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
            runOnUiThread{checkAlert(coin,p);coinCard(coin,p,change,signal,bid)}
        }catch(e:Exception){runOnUiThread{results.addView(t("$coin  // DATA LINK UNAVAILABLE",14f,Color.LTGRAY))}}}}
    }

    private fun checkAlert(coin:String,price:Double){
        val prefs=getSharedPreferences("neo",MODE_PRIVATE);val target=prefs.getFloat("alert_"+coin,Float.NaN)
        if(!target.isNaN() && price>=target.toDouble()){Toast.makeText(this,coin+" alert reached: $"+fmt(price),Toast.LENGTH_LONG).show()}
    }

    private fun coinCard(c:String,p:Double,ch:Double,s:String,bid:Double){
        val accent=if(s=="ENTER")green else if(s=="NO ACTION")Color.rgb(255,75,55) else gold;val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=panel(accent)}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};top.addView(t(c+" // USDT",20f,gold).apply{typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));top.addView(t(" "+s+" ",13f,accent).apply{gravity=Gravity.CENTER;background=panel(accent)});card.addView(top)
        card.addView(t("$"+fmt(p),28f,Color.WHITE).apply{typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)});card.addView(t("24H VECTOR  "+(if(ch>=0)"▲ " else "▼ ")+"%.2f".format(abs(ch))+"%   //   IDEAL BID  $"+fmt(bid),13f,if(ch>=0)green else Color.rgb(255,90,70)));card.addView(t("ANALYSIS // "+s+"     CONFIDENCE VECTOR // EXPERIMENTAL",11f,accent));val meter=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progress=((abs(ch)*10)+35).toInt().coerceIn(35,95);progressTintList=android.content.res.ColorStateList.valueOf(accent);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(20,45,32))};card.addView(meter,LinearLayout.LayoutParams(-1,8).apply{setMargins(12,2,12,2)});card.addView(SparkView(this,ch),LinearLayout.LayoutParams(-1,90))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};actions.addView(neoButton("COIN DETAIL",green){detail(c,p,ch,s,bid)},LinearLayout.LayoutParams(0,-2,1f));actions.addView(neoButton("ARM ALERT",gold){alertDialog(c,p)},LinearLayout.LayoutParams(0,-2,1f));card.addView(actions);results.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,8)})
    }

    private fun screen(title:String):LinearLayout{
        val f=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)};f.addView(MatrixView(this));val sc=ScrollView(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,30,24,80)}
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};nav.addView(neoButton("‹ BACK",gold){home()},LinearLayout.LayoutParams(0,-2,.35f));nav.addView(t(title,21f,Color.WHITE).apply{gravity=Gravity.CENTER;typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));box.addView(nav);box.addView(spacer(16));sc.addView(box);f.addView(sc);setContentView(f);return box
    }
    private fun markets(){
        val box=screen("LIVE MARKETS");val search=EditText(this).apply{hint="Search coins...";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);setPadding(18,16,18,16);background=panel(green)};box.addView(search);box.addView(spacer(10))
        box.addView(t("ALL     FAVOURITES     GAINERS     LOSERS",12f,gold));box.addView(spacer(6))
        watch.forEach { coin ->
            thread {
                try {
                    val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol="+coin+"USDT").readText())
                    val p=j.getDouble("lastPrice"); val ch=j.getDouble("priceChangePercent")
                    runOnUiThread {
                        val r=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; background=panel(if(ch>=0)green else Color.rgb(255,75,55)); setPadding(12,8,12,8) }
                        r.addView(t(coin,17f,gold),LinearLayout.LayoutParams(0,-2,.45f))
                        r.addView(t("$"+fmt(p),14f,Color.WHITE),LinearLayout.LayoutParams(0,-2,.75f))
                        r.addView(t((if(ch>=0)"+ " else "")+"%.2f".format(ch)+"%",13f,if(ch>=0)green else Color.rgb(255,75,55)),LinearLayout.LayoutParams(0,-2,.55f))
                        r.setOnClickListener { chart(coin) }
                        box.addView(r,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)})
                    }
                } catch (_:Exception) { }
            }
        }
    }
    private fun detail(c:String,p:Double,ch:Double,s:String,bid:Double){
        val box=screen(c+" / USDT");box.addView(t("$"+fmt(p),29f,Color.WHITE));box.addView(t((if(ch>=0)"▲ +" else "▼ ")+"%.2f".format(ch)+"% (24h)",14f,if(ch>=0)green else Color.RED));box.addView(spacer(12))
        val intelligence=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,14,16,14);background=panel(gold)};intelligence.addView(t("AI ACTION     "+s,16f,gold));intelligence.addView(t("◎ IDEAL BID     $"+fmt(bid),14f,green));intelligence.addView(t("◉ TARGET 1      $"+fmt(p*1.015),14f));intelligence.addView(t("◉ TARGET 2      $"+fmt(p*1.035),14f));intelligence.addView(t("◇ STOP LOSS     $"+fmt(p*.975),14f,Color.rgb(255,90,70)));box.addView(intelligence);box.addView(spacer(12));box.addView(neoButton("LIVE CANDLES",green){chart(c)});box.addView(spacer(8));box.addView(neoButton("♢ SET ALERT",gold){alertDialog(c,p)});box.addView(spacer(10));box.addView(t("1m    5m    15m    1h    4h    1D",12f,green))
    }
    private fun alertCenter(){
        val box=screen("SET ALERT");box.addView(t("CUSTOM PRICE & % ALERTS",13f,gold))
        box.addView(t("PRICE     % CHANGE     INDICATOR",12f,green))
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,12);background=panel(green)}
        card.addView(t("Alert me when price is",13f,Color.WHITE))
        card.addView(t("ABOVE     selected price        ●",13f,green))
        card.addView(t("BELOW     selected price        ●",13f,green))
        card.addView(t("% CHANGE (24H)",13f,Color.WHITE));card.addView(t("GREATER THAN   +5%             ●",13f,green));card.addView(t("LESS THAN      -5%             ○",13f,Color.LTGRAY))
        card.addView(t("NOTIFICATION METHOD",12f,gold));card.addView(t("◉ PUSH NOTIFICATION             ●",13f,green));card.addView(t("◇ SOUND        MATRIX TONE",13f));card.addView(t("↻ REPEAT       UNTIL CANCELLED",13f))
        box.addView(card);box.addView(spacer(12))
        watch.forEach{coin->box.addView(neoButton(coin+" / USDT  //  CREATE ALERT",green){thread{try{val p=JSONObject(URL("https://api.binance.com/api/v3/ticker/price?symbol="+coin+"USDT").readText()).getDouble("price");runOnUiThread{alertDialog(coin,p)}}catch(_:Exception){}}})}
    }

    private fun watchlist(){
        val box=screen("WATCHLIST");val add=neoButton("+ ADD COIN",gold){markets()};box.addView(add);box.addView(spacer(10));watch.forEach{coin->val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=panel(green);setPadding(12,8,12,8)};r.addView(t("☆  "+coin,16f,gold),LinearLayout.LayoutParams(0,-2,1f));r.addView(t("TRACKING",11f,green));r.setOnClickListener{markets()};box.addView(r,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,4)})};box.addView(spacer(14));box.addView(t("WATCHLIST NEWS // INTELLIGENCE FEED",13f,gold));val news=listOf("BTC  //  MARKET INTELLIGENCE FEED","ETH  //  NETWORK ACTIVITY FEED","SOL  //  ECOSYSTEM ACTIVITY FEED");news.forEach{box.addView(t(it+"\nLive news connection pending.",12f,Color.LTGRAY).apply{background=panel(gold)},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,4)})}
    }
    private fun menu(){
        val box=screen("N E O // MENU");listOf("⌂  DASHBOARD","▥  MARKETS","◎  SCANNER","★  WATCHLIST","♢  ALERTS","▤  TRADE LOG","⚙  SETTINGS","◈  CONNECTION","◐  APPEARANCE","?  HELP").forEach{label->box.addView(neoButton(label,if(label.contains("DASHBOARD"))green else Color.LTGRAY){when{label.contains("MARKETS")->markets();label.contains("SCANNER")->home();label.contains("WATCHLIST")->watchlist();label.contains("ALERTS")->alertCenter();else->Toast.makeText(this,label.substringAfter("  ")+" module",Toast.LENGTH_SHORT).show()}},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,3,0,3)})}
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

    private fun alertDialog(c:String,p:Double){
        val input=EditText(this).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(fmt(p))}
        AlertDialog.Builder(this).setTitle("$c price alert").setMessage("Notify when a future scan reaches this USDT price").setView(input)
            .setPositiveButton("SAVE"){_,_->val v=input.text.toString().toDoubleOrNull();if(v!=null){getSharedPreferences("neo_alerts",MODE_PRIVATE).edit().putString(c,v.toString()).putString(c+"_last",p.toString()).apply();Toast.makeText(this,"Alert armed for $c at ${fmt(v)}",Toast.LENGTH_LONG).show()}}.setNegativeButton("CANCEL",null).show()
    }
    private fun fmt(v:Double)=if(v>=100)"%.2f".format(v) else if(v>=1)"%.4f".format(v) else "%.6f".format(v)

    class MatrixView(c:Context):View(c){
        val p=Paint().apply{color=Color.rgb(0,120,55);textSize=18f;typeface=Typeface.MONOSPACE};var tick=0
        override fun onDraw(x:Canvas){super.onDraw(x);x.drawColor(Color.rgb(1,7,4));val chars="01{}<>#@+";val cols=(width/34).coerceAtLeast(1);for(i in 0..cols){for(j in 0..(height/48)){val y=j*48+(tick+i*37)%48;val ch=chars[(i*7+j*3+tick)%chars.length].toString();p.alpha=35+((i+j+tick)%5)*30;x.drawText(ch,(i*34).toFloat(),y.toFloat(),p)}};tick=(tick+2)%500;postInvalidateDelayed(70)}
    }
    class RadarView(c:Context):View(c){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2.5f;color=Color.rgb(0,255,119)};var a=0f
        override fun onDraw(x:Canvas){super.onDraw(x);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.43f;p.alpha=70;for(k in 1..5)x.drawCircle(cx,cy,r*k/5,p);for(d in 0 until 360 step 30){val q=Math.toRadians(d.toDouble());x.drawLine(cx,cy,cx+cos(q).toFloat()*r,cy+sin(q).toFloat()*r,p)};for(n in 0..8){val q=Math.toRadians((a-n*5).toDouble());p.alpha=(210-n*20).coerceAtLeast(25);x.drawLine(cx,cy,cx+cos(q).toFloat()*r,cy+sin(q).toFloat()*r,p)};p.style=Paint.Style.FILL;p.alpha=240;listOf(Pair(.34f,.22f),Pair(-.48f,.16f),Pair(.18f,-.55f)).forEach{q->x.drawCircle(cx+r*q.first,cy+r*q.second,6f,p)};p.style=Paint.Style.STROKE;a=(a+3.2f)%360;postInvalidateDelayed(28)}}
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