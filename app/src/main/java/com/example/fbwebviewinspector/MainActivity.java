package com.example.fbwebviewinspector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.*;
import android.widget.*;
import android.view.*;
import android.os.SystemClock;
import org.json.*;

public class MainActivity extends Activity {
    WebView web; TextView results,status,spacerValue;
    Button scan,testReply,testLike,stopTimer;
    EditText intervalInput;
    View spacer;
    SeekBar spacerSeek;
    Handler handler=new Handler();
    Runnable timerTask;
    int runIndex=0;
    long intervalMs=10000;

    String scanJs = "" +
      "(()=>{const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" +
      "const labels=['reply','replies','comment','like','likes','react','reaction','رد','الرد','الردود','إعجاب','اعجاب','أعجبني','عرض الردود','رد على','مشاركة'];" +
      "const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];" +
      "const out=[];for(const e of els){const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),r=norm(e.getAttribute('role')),title=norm(e.getAttribute('title'));" +
      "const hay=(t+' '+a+' '+title).toLowerCase();if(labels.some(x=>hay.includes(x.toLowerCase()))){out.push({tag:e.tagName,text:t.slice(0,120),aria:a,role:r,title:title,clickable:!!(e.onclick||e.getAttribute('tabindex')!==null||r==='button'||r==='link')});}}" +
      "return JSON.stringify({url:location.href,title:document.title,count:out.length,items:out.slice(0,80)});})()";

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle b){
      super.onCreate(b); setContentView(R.layout.activity_main);
      web=findViewById(R.id.web); results=findViewById(R.id.results); status=findViewById(R.id.status);
      scan=findViewById(R.id.scan); testReply=findViewById(R.id.testReply); testLike=findViewById(R.id.testLike);
      stopTimer=findViewById(R.id.stopTimer);
      intervalInput=findViewById(R.id.intervalInput);
      spacer=findViewById(R.id.spacer); spacerSeek=findViewById(R.id.spacerSeek); spacerValue=findViewById(R.id.spacerValue);

      WebSettings s=web.getSettings();
      s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
      s.setSupportMultipleWindows(false);
      s.setUserAgentString(s.getUserAgentString()+" FacebookWebViewInspector/1.2");
      web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){status.setText("تم تحميل الصفحة: "+u);}});
      web.loadUrl("https://www.facebook.com/");

      scan.setOnClickListener(v->runScan());
      testReply.setOnClickListener(v->runReplyAccessTest());
      testLike.setOnClickListener(v->startRepeatedLike());
      stopTimer.setOnClickListener(v->stopRepeatedDetection());

      spacerSeek.setMax(300);
      spacerSeek.setProgress(40);
      setSpacer(40);
      spacerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
        public void onProgressChanged(SeekBar s,int p,boolean fromUser){setSpacer(p);}
        public void onStartTrackingTouch(SeekBar s){}
        public void onStopTrackingTouch(SeekBar s){}
      });
    }

    void setSpacer(int dp){
      int px=(int)(dp*getResources().getDisplayMetrics().density+0.5f);
      spacer.getLayoutParams().height=px; spacer.requestLayout();
      spacerValue.setText("المسافة بين أدوات التطبيق وفيسبوك: "+dp+"dp");
    }

    void runScan(){
      status.setText("جاري فحص DOM...");
      web.evaluateJavascript(scanJs,val->{
        try{
          JSONObject o=new JSONObject(unquote(val)); JSONArray a=o.getJSONArray("items");
          StringBuilder sb=new StringBuilder();
          sb.append("URL: ").append(o.optString("url")).append("\nعدد العناصر المطابقة: ").append(a.length()).append("\n\n");
          for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);
            sb.append(i+1).append(") ").append(x.optString("text")).append(" | aria=").append(x.optString("aria"))
              .append(" | role=").append(x.optString("role")).append(" | clickable=").append(x.optBoolean("clickable")).append("\n");}
          results.setText(sb.toString()); status.setText("اكتمل الفحص");
        }catch(Exception e){results.setText(val); status.setText("تعذر تحليل نتيجة الفحص");}
      });
    }

    void runTest(String kind){
      status.setText("جاري تنفيذ "+kind+"...");
      web.evaluateJavascript(buildClickJs(kind),val->{
        try{
          JSONObject o=new JSONObject(unquote(val));
          results.setText(o.toString(2));
          status.setText(o.optString("message","انتهى التنفيذ"));
        }catch(Exception e){
          results.setText(val);
          status.setText("انتهى التنفيذ");
        }
      });
    }


    void appendLog(final String msg){
      runOnUiThread(new Runnable(){ public void run(){
        if(results==null)return;
        results.append((results.length()>0?"\n":"")+msg);
        status.setText(msg);
      }});
    }

    void runReplyAccessTest(){
      stopRepeatedDetection();
      results.setText("=== اختبار الوصول إلى Reply ===");
      status.setText("1/6 — البحث عن Like صالح...");
      web.evaluateJavascript(buildReplyAccessJs(), new ValueCallback<String>(){
        @Override public void onReceiveValue(String val){
          try{
            JSONObject o=new JSONObject(unquote(val));
            appendLog("\n"+o.optString("log","انتهى الاختبار"));
            status.setText(o.optString("message","انتهى الاختبار"));
            if(o.optBoolean("needTouch",false)){
              float x=(float)o.optDouble("x",-1);
              float y=(float)o.optDouble("y",-1);
              if(x>=0&&y>=0){
                handler.postDelayed(new Runnable(){ public void run(){ dispatchWebTouch(x,y); }},350);
              }
            }
          }catch(Exception e){
            results.append("\nتعذر تحليل نتيجة الاختبار: "+val);
            status.setText("تعذر تحليل نتيجة الاختبار");
          }
        }
      });
    }

    void dispatchWebTouch(float cssX,float cssY){
      float scale=web.getScale();
      float x=cssX*scale;
      float y=cssY*scale;
      long down=SystemClock.uptimeMillis();
      MotionEvent e1=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);
      MotionEvent e2=MotionEvent.obtain(down,down+80,MotionEvent.ACTION_UP,x,y,0);
      boolean a=web.dispatchTouchEvent(e1);
      boolean b=web.dispatchTouchEvent(e2);
      e1.recycle(); e2.recycle();
      appendLog("\n[5/6] تم إرسال ضغطة Touch إلى Reply: x="+x+" y="+y+" (scale="+scale+") — DOWN="+a+" UP="+b);
      handler.postDelayed(new Runnable(){ public void run(){
        web.evaluateJavascript("(()=>{const n=document.elementFromPoint("+cssX+","+cssY+");return JSON.stringify({tag:n?n.tagName:'',text:n?(n.innerText||'').slice(0,100):'',aria:n?(n.getAttribute('aria-label')||''):'',role:n?(n.getAttribute('role')||''):''});})()", new ValueCallback<String>(){ public void onReceiveValue(String v){ appendLog("[6/6] العنصر عند نقطة Reply بعد الضغطة: "+unquote(v)); }});
      }},500);
    }

    void startRepeatedLike(){
      stopRepeatedDetection();
      try{
        intervalMs=Math.max(1000,Long.parseLong(intervalInput.getText().toString().trim())*1000L);
      }catch(Exception e){
        intervalMs=10000;
      }

      runIndex=0;
      results.setText("بدأ اللايك التلقائي.\nالفاصل الزمني: "+(intervalMs/1000)+" ثانية.\nاضغط «إيقاف» لإنهاء التكرار.");
      status.setText("جاري تنفيذ اللايك...");

      timerTask=new Runnable(){
        @Override public void run(){
          runIndex++;
          final int n=runIndex;

          web.evaluateJavascript(buildActualLikeJs(),val->{
            try{
              JSONObject o=new JSONObject(unquote(val));
              String line="الدورة "+n+" — "+o.optString("message");
              results.append("\n"+line);
              status.setText("الدورة "+n+" — "+o.optString("message"));
            }catch(Exception e){
              results.append("\nالدورة "+n+" — تعذر قراءة النتيجة");
              status.setText("الدورة "+n);
            }
          });

          if(timerTask!=null){
            handler.postDelayed(this,intervalMs);
          }
        }
      };

      handler.post(timerTask);
    }

    void stopRepeatedDetection(){
      if(timerTask!=null) handler.removeCallbacks(timerTask);
      timerTask=null;
      if(status!=null) status.setText("تم إيقاف اللايك التلقائي");
    }

    String buildActualLikeJs(){
      return "(()=>{const keys=['like','likes','إعجاب','اعجاب','أعجبني'];" +
      "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" +
      "const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];" +
      "let hit=null,best=-1;" +
      "for(const e of els){" +
      "const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),title=norm(e.getAttribute('title'))," +
      "pressed=norm(e.getAttribute('aria-pressed')),q=e.getBoundingClientRect();" +
      "if(q.width<=0||q.height<=0)continue;" +
      "const hay=(t+' '+a+' '+title).toLowerCase();" +
      "if(/unlike|remove like|إلغاء الإعجاب|إلغاء اعجاب|أعجبني/.test(a.toLowerCase()) && a.toLowerCase().indexOf('like')>=0)continue;" +
      "if(pressed==='true')continue;" +
      "let score=0;" +
      "for(const x of keys){" +
      "if(a.toLowerCase()===x.toLowerCase())score+=100;" +
      "if(title.toLowerCase()===x.toLowerCase())score+=80;" +
      "if(t.toLowerCase()===x.toLowerCase())score+=70;" +
      "if(hay.includes(x.toLowerCase()))score+=10;}" +
      "if((e.getAttribute('role')==='button'||e.tagName==='BUTTON')&&score>0)score+=20;" +
      "if(score>best){best=score;hit=e;}" +
      "}" +
      "if(!hit)return JSON.stringify({ok:false,clicked:false,message:'لم يتم العثور على زر Like غير مُعجب به ظاهر'});" +
      "hit.scrollIntoView({block:'center',inline:'center'});" +
      "const before=norm(hit.innerText)+' | '+norm(hit.getAttribute('aria-label'))+' | '+norm(hit.getAttribute('aria-pressed'));" +
      "hit.click();" +
      "return JSON.stringify({ok:true,clicked:true,tag:hit.tagName,text:norm(hit.innerText)," +
      "aria:norm(hit.getAttribute('aria-label')),role:norm(hit.getAttribute('role')),before:before,score:best," +
      "message:'تم تنفيذ Like فعليًا'});})()";
    }

    String buildReplyAccessJs(){
      return "(()=>{const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"+
      "const all=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];"+
      "const vis=e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&r.bottom>=0&&r.right>=0&&r.top<=innerHeight&&r.left<=innerWidth};"+
      "const likeKeys=['like','likes','إعجاب','اعجاب','أعجبني'];const replyKeys=['reply','replies','رد','الرد','الردود','رد على','عرض الردود'];"+
      "const score=(e,keys)=>{if(!vis(e))return -1;const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),ti=norm(e.getAttribute('title')),h=(t+' '+a+' '+ti).toLowerCase();let s=0;for(const k of keys){const q=k.toLowerCase();if(a.toLowerCase()===q)s+=100;if(ti.toLowerCase()===q)s+=80;if(t.toLowerCase()===q)s+=70;if(h.includes(q))s+=10;}if(e.getAttribute('role')==='button'||e.tagName==='BUTTON')s+=20;return s};"+
      "let like=null,lb=-1;for(const e of all){const a=norm(e.getAttribute('aria-label')).toLowerCase(),p=norm(e.getAttribute('aria-pressed'));if(/unlike|remove like|إلغاء الإعجاب|إلغاء اعجاب/.test(a)||p==='true')continue;const s=score(e,likeKeys);if(s>lb){lb=s;like=e;}}"+
      "if(!like)return JSON.stringify({ok:false,message:'لم يتم العثور على Like صالح',log:'[1/6] لم يتم العثور على Like صالح ظاهر.'});"+
      "like.scrollIntoView({block:'center',inline:'center'});const likeText=norm(like.innerText)||norm(like.getAttribute('aria-label'));try{like.click();}catch(e){};"+
      "const wait=(ms)=>new Promise(r=>setTimeout(r,ms));return wait(700).then(()=>{const all2=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];"+
      "const lr=like.getBoundingClientRect();let root=like;for(let i=0;i<8&&root.parentElement;i++){const p=root.parentElement;const txt=norm(p.innerText).toLowerCase();root=p;if(txt.includes('reply')||txt.includes('رد'))break;}"+
      "const candidates=all2.filter(e=>replyKeys.some(k=>{const h=(norm(e.innerText)+' '+norm(e.getAttribute('aria-label'))+' '+norm(e.getAttribute('title'))).toLowerCase();return h===k.toLowerCase()||h.includes(k.toLowerCase());})&&vis(e));"+
      "let best=null,bs=-1;for(const e of candidates){const r=e.getBoundingClientRect();const dx=Math.abs((r.left+r.width/2)-(lr.left+lr.width/2));const dy=Math.abs((r.top+r.height/2)-(lr.top+lr.height/2));let near=0;if(dy<90)near+=80;if(dx<350)near+=60;let same=0;if(root.contains(e))same=200;const sc=near+same+Math.max(0,100-Math.min(100,dx/3));if(sc>bs){bs=sc;best=e;}}"+
      "if(!best)return JSON.stringify({ok:false,message:'لم يتم العثور على Reply قريب من Like',log:'[1/6] تم العثور على Like\\n[2/6] Like: '+likeText+'\\n[3/6] تم فحص '+candidates.length+' مرشح Reply ولم يتم العثور على واحد قريب.'});"+
      "best.scrollIntoView({block:'center',inline:'center'});const rr=best.getBoundingClientRect();const cx=rr.left+rr.width/2,cy=rr.top+rr.height/2;const info={tag:best.tagName,text:norm(best.innerText),aria:norm(best.getAttribute('aria-label')),role:norm(best.getAttribute('role')),x:cx,y:cy,w:rr.width,h:rr.height,dx:Math.abs(cx-(lr.left+lr.width/2)),dy:Math.abs(cy-(lr.top+lr.height/2))};"+
      "let clicked=false;try{best.click();clicked=true;}catch(e){}"+
      "return JSON.stringify({ok:true,needTouch:true,x:cx,y:cy,clicked:clicked,message:'تم العثور على Reply ومحاولة الضغط عليه',log:'[1/6] تم العثور على Like\\n[2/6] Like: '+likeText+'\\n[3/6] عدد مرشحي Reply: '+candidates.length+'\\n[4/6] تم العثور على Reply قريب من Like: '+JSON.stringify(info)+'\\n[5/6] تم تنفيذ click على العنصر، وسيتم الآن إرسال Touch حقيقي إلى نفس الإحداثيات.'});});})()";
    }

    String buildClickJs(String kind){
      String[] keys=kind.equals("reply")?new String[]{"reply","replies","رد","الرد","الردود","رد على","عرض الردود"}:new String[]{"like","likes","إعجاب","اعجاب","أعجبني"};
      String arr=new JSONArray(java.util.Arrays.asList(keys)).toString(),k=kind;
      return "(()=>{const keys="+arr+";const norm=s=>(s||'').replace(/\\s+/g,' ').trim();const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];let hit=null,best=-1;" +
      "for(const e of els){const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),title=norm(e.getAttribute('title')),q=e.getBoundingClientRect();if(q.width<=0||q.height<=0)continue;const hay=(t+' '+a+' '+title).toLowerCase();let score=0;" +
      "for(const x of keys){if(a.toLowerCase()===x.toLowerCase())score+=100;if(title.toLowerCase()===x.toLowerCase())score+=80;if(t.toLowerCase()===x.toLowerCase())score+=70;if(hay.includes(x.toLowerCase()))score+=10;}if((e.getAttribute('role')==='button'||e.tagName==='BUTTON')&&score>0)score+=20;if(score>best){best=score;hit=e;}}" +
      "if(!hit)return JSON.stringify({ok:false,clicked:false,kind:'"+k+"',message:'لم يتم العثور على زر "+k+" قابل للنقر'});hit.scrollIntoView({block:'center',inline:'center'});const before=norm(hit.innerText)+' | '+norm(hit.getAttribute('aria-label'))+' | '+norm(hit.getAttribute('aria-pressed'));hit.click();" +
      "return JSON.stringify({ok:true,clicked:true,kind:'"+k+"',tag:hit.tagName,text:norm(hit.innerText),aria:norm(hit.getAttribute('aria-label')),role:norm(hit.getAttribute('role')),before:before,score:best,message:'تم النقر تلقائيًا على أول زر مطابق. تم تسجيل الحالة قبل النقر.'});})()";
    }

    static String unquote(String s){if(s==null)return "";if(s.startsWith("\"")&&s.endsWith("\"")){try{return new org.json.JSONTokener(s).nextValue().toString();}catch(Exception ignored){}}return s;}
    @Override protected void onDestroy(){stopRepeatedDetection();super.onDestroy();}
}
