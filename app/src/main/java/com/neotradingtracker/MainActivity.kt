package com.neotradingtracker

import android.app.*
import android.os.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.*

class MainActivity : Activity() {
    private val green=Color.rgb(0,255,119); private val gold=Color.rgb(255,205,80)
    private lateinit var body: LinearLayout
    private val watch=linkedSetOf("BTC","ETH","XRP","SOL","DOGE")

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.BLACK;boot()}
    fun t(s:String,sz:Float=16f,c:Int=green)=TextView(this).apply{text=s;textSize=sz;setTextColor(c);typeface=Typeface.MONOSPACE;setPadding(10,10,10,10)}

    private fun boot(){
        val f=FrameLayout(this);f.setBackgroundColor(Color.rgb(1,8,5));f.addView(MatrixView(this))
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(28,28,28,28)}
        val terminal=t("",20f);box.addView(t("NEO TRADING TRACKER",28f,gold));box.addView(terminal);f.addView(box);setContentView(f)
        val lines=listOf("> SYSTEM ACCESS...","You've hacked it Neo.","Knock knock.","☎  SECURE LINE CONNECTED")
        var i=0;val h=Handler(mainLooper);val r=object:Runnable{override fun run(){if(i<lines.size){terminal.append(lines[i++]+"\n");h.postDelayed(this,700)}else h.postDelayed({home()},900)}};h.post(r)
    }

    private fun home(){
        val f=FrameLayout(this);f.setBackgroundColor(Color.BLACK);f.addView(MatrixView(this))
        val scroll=ScrollView(this);body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,28,22,80)}
        body.addView(t("NEO TRADING TRACKER",26f,gold));body.addView(t("SECURE MARKET INTELLIGENCE // LIVE",12f))
        val search=EditText(this).apply{hint="ADD COIN  e.g. ADA";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);backgroundTintList=android.content.res.ColorStateList.valueOf(green)}
        body.addView(search)
        body.addView(Button(this).apply{text="FOLLOW COIN";setOnClickListener{val s=search.text.toString().uppercase().replace("USDT","").trim();if(s.isNotEmpty()){watch.add(s);search.setText("");scan()}}})
        body.addView(Button(this).apply{text="◉  SCAN NOW";textSize=20f;setTextColor(Color.BLACK);setBackgroundColor(green);setOnClickListener{scan()}})
        body.addView(RadarView(this),LinearLayout.LayoutParams(-1,380));scroll.addView(body);f.addView(scroll);setContentView(f);scan()
    }

    private fun scan(){
        while(body.childCount>6)body.removeViewAt(6)
        body.addView(t("SCANNING ${watch.size} TARGETS...",14f,gold))
        watch.forEach{coin->thread{try{
            val j=JSONObject(URL("https://api.binance.com/api/v3/ticker/24hr?symbol=${coin}USDT").readText())
            val p=j.getDouble("lastPrice");val low=j.getDouble("lowPrice");val high=j.getDouble("highPrice");val change=j.getDouble("priceChangePercent")
            val range=(high-low).coerceAtLeast(p*.001);val pos=(p-low)/range
            val signal=when{change>1.0&&pos<.82->"ENTER";change< -3.0||pos>.94->"NO ACTION";else->"WAIT"}
            val bid=when(signal){"ENTER"->p-range*.08;"WAIT"->p-range*.16;else->low+range*.25}
            runOnUiThread{coinCard(coin,p,change,signal,bid)}
        }catch(e:Exception){runOnUiThread{body.addView(t("$coin  // DATA LINK UNAVAILABLE",14f,Color.LTGRAY))}}}}
    }

    private fun coinCard(c:String,p:Double,ch:Double,s:String,bid:Double){
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18);background=ColorDrawable(Color.argb(220,3,22,14))}
        card.addView(t("$c / USDT     ${fmt(p)}",20f,gold))
        card.addView(t("24H ${"%.2f".format(ch)}%     SIGNAL: $s",17f,if(s=="ENTER")green else if(s=="NO ACTION")Color.RED else gold))
        card.addView(t("IDEAL BID  $${fmt(bid)}",17f))
        card.addView(Button(this).apply{text="LIVE CANDLES";setOnClickListener{chart(c)}})
        card.addView(Button(this).apply{text="SET PRICE ALERT";setOnClickListener{alertDialog(c,p)}})
        body.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,10,0,10)})
    }

    private fun chart(c:String){
        val w=WebView(this);w.settings.javaScriptEnabled=true;w.setBackgroundColor(Color.BLACK)
        w.loadUrl("https://www.tradingview.com/chart/?symbol=BINANCE%3A${c}USDT");setContentView(w)
        w.setOnKeyListener{_,key,e->if(key==KeyEvent.KEYCODE_BACK&&e.action==KeyEvent.ACTION_UP){home();true}else false}
    }

    private fun alertDialog(c:String,p:Double){
        val input=EditText(this).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(fmt(p))}
        AlertDialog.Builder(this).setTitle("$c price alert").setMessage("Notify when a future scan reaches this USDT price").setView(input)
            .setPositiveButton("SAVE"){_,_->Toast.makeText(this,"Alert armed for $c at $${input.text}",Toast.LENGTH_LONG).show()}.setNegativeButton("CANCEL",null).show()
    }
    private fun fmt(v:Double)=if(v>=100)"%.2f".format(v) else if(v>=1)"%.4f".format(v) else "%.6f".format(v)

    class MatrixView(c:Context):View(c){
        val p=Paint().apply{color=Color.rgb(0,120,55);textSize=18f;typeface=Typeface.MONOSPACE};var tick=0
        override fun onDraw(x:Canvas){super.onDraw(x);x.drawColor(Color.rgb(1,7,4));val chars="01{}<>#@+";val cols=(width/34).coerceAtLeast(1);for(i in 0..cols){for(j in 0..(height/48)){val y=j*48+(tick+i*37)%48;val ch=chars[(i*7+j*3+tick)%chars.length].toString();p.alpha=35+((i+j+tick)%5)*30;x.drawText(ch,(i*34).toFloat(),y.toFloat(),p)}};tick=(tick+2)%500;postInvalidateDelayed(70)}
    }
    class RadarView(c:Context):View(c){
        val p=Paint().apply{style=Paint.Style.STROKE;strokeWidth=3f;color=Color.rgb(0,255,119)};var a=0f
        override fun onDraw(x:Canvas){super.onDraw(x);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.42f;p.alpha=130;for(k in 1..4)x.drawCircle(cx,cy,r*k/4,p);x.drawLine(cx-r,cy,cx+r,cy,p);x.drawLine(cx,cy-r,cx,cy+r,p);p.alpha=255;val ex=cx+cos(Math.toRadians(a.toDouble())).toFloat()*r;val ey=cy+sin(Math.toRadians(a.toDouble())).toFloat()*r;x.drawLine(cx,cy,ex,ey,p);a=(a+4)%360;postInvalidateDelayed(35)}
    }
}