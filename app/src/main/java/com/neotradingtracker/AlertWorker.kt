package com.neotradingtracker
import android.app.*
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import org.json.JSONObject
import java.net.*
class AlertWorker(ctx:Context,p:WorkerParameters):Worker(ctx,p){
 override fun doWork():Result{
  val prefs=applicationContext.getSharedPreferences("neo_alerts",Context.MODE_PRIVATE)
  val symbols=prefs.getStringSet("armed_symbols",emptySet())?:emptySet()
  symbols.forEach{coin->
   val target=prefs.getString(coin,null)?.toDoubleOrNull()?:return@forEach
   try{
    val con=URL("https://api.binance.com/api/v3/ticker/price?symbol="+coin+"USDT").openConnection() as HttpURLConnection
    con.connectTimeout=7000;con.readTimeout=7000
    val price=JSONObject(con.inputStream.bufferedReader().use{it.readText()}).getDouble("price")
    val last=prefs.getString(coin+"_last",null)?.toDoubleOrNull()
    if(last!=null&&((last<target&&price>=target)||(last>target&&price<=target))) notifyAlert(coin,price,target)
    prefs.edit().putString(coin+"_last",price.toString()).apply()
   }catch(_:Exception){}
  };return Result.success()
 }
 private fun notifyAlert(c:String,p:Double,t:Double){
  val nm=applicationContext.getSystemService(NotificationManager::class.java)
  if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("neo_alerts","Neo Price Alerts",NotificationManager.IMPORTANCE_HIGH))
  nm.notify(c.hashCode(),NotificationCompat.Builder(applicationContext,"neo_alerts").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(c+" / USDT ALERT").setContentText("Price "+p+" crossed target "+t).setPriority(NotificationCompat.PRIORITY_HIGH).build())
 }
}