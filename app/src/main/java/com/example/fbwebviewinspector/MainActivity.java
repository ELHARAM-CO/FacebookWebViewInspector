package com.example.fbwebviewinspector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.*;
import android.widget.*;
import android.view.*;
import org.json.*;

public class MainActivity extends Activity {
    WebView web; TextView results,status,spacerValue;
    Button scan,testReply,testLike,stopTimer;
    EditText intervalInput, replyInput;
    View spacer;
    SeekBar spacerSeek;
    Handler handler=new Handler();
    Runnable timerTask, replyTimerTask;
    int runIndex=0, replyRunIndex=0;
    long intervalMs=10000, replyIntervalMs=10000;

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
      replyInput=findViewById(R.id.replyInput);
      spacer=findViewById(R.id.spacer); spacerSeek=findViewById(R.id.spacerSeek); spacerValue=findViewById(R.id.spacerValue);

      WebSettings s=web.getSettings();
      s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
      s.setSupportMultipleWindows(false);
      s.setUserAgentString(s.getUserAgentString()+" FacebookWebViewInspector/1.2");
      web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){status.setText("تم تحميل الصفحة: "+u);}});
      web.loadUrl("https://www.facebook.com/");

      scan.setOnClickListener(v->runScan());
      testReply.setOnClickListener(v->startRepeatedReply());
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

    void startRepeatedReply(){
      if(replyTimerTask!=null) handler.removeCallbacks(replyTimerTask);
      try{
        replyIntervalMs=Math.max(1000,Long.parseLong(intervalInput.getText().toString().trim())*1000L);
      }catch(Exception e){ replyIntervalMs=10000; }
      String text=replyInput.getText().toString().trim();
      if(text.isEmpty()){
        status.setText("اكتب نص الرد أولًا");
        return;
      }
      replyRunIndex=0;
      results.setText("بدأ الـReply التلقائي.\nالنص: "+text+"\nالفاصل الزمني: "+(replyIntervalMs/1000)+" ثانية.\nاضغط «إيقاف» لإنهاء التكرار.");
      status.setText("جاري تنفيذ الـReply...");
      replyTimerTask=new Runnable(){
        @Override public void run(){
          replyRunIndex++;
          final int n=replyRunIndex;
          web.evaluateJavascript(buildActualReplyJs(text),val->{
            try{
              JSONObject o=new JSONObject(unquote(val));
              if(o.optBoolean("ok")){
                handler.postDelayed(()->web.evaluateJavascript(buildFillReplyJs(text),val2->{
                  try{ JSONObject o2=new JSONObject(unquote(val2)); String line="Reply "+n+" — "+o2.optString("message"); results.append("\n"+line); status.setText(line); }
                  catch(Exception e){ results.append("\nReply "+n+" — تم فتح Reply لكن تعذر إكمال الإرسال"); status.setText("Reply "+n); }
                }),700);
              }else{
                String line="Reply "+n+" — "+o.optString("message"); results.append("\n"+line); status.setText(line);
              }
            }catch(Exception e){
              results.append("\nReply "+n+" — تعذر قراءة النتيجة");
              status.setText("Reply "+n);
            }
          });
          if(replyTimerTask!=null) handler.postDelayed(this,replyIntervalMs);
        }
      };
      handler.post(replyTimerTask);
    }

    void stopRepeatedDetection(){
      if(timerTask!=null) handler.removeCallbacks(timerTask);
      timerTask=null;
      if(replyTimerTask!=null) handler.removeCallbacks(replyTimerTask);
      replyTimerTask=null;
      if(status!=null) status.setText("تم إيقاف التشغيل التلقائي");
    }

    String buildActualReplyJs(String replyText){
      String jsText=org.json.JSONObject.quote(replyText);
      return "(()=>{const replyText="+jsText+";const norm=s=>(s||'').replace(/\s+/g,' ').trim();"+
      "const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];"+
      "let hit=null,best=-1;const keys=['reply','replies','رد','الرد','الردود','رد على','عرض الردود'];"+
      "for(const e of els){const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),title=norm(e.getAttribute('title')),q=e.getBoundingClientRect();"+
      "if(q.width<=0||q.height<=0)continue;const hay=(t+' '+a+' '+title).toLowerCase();let score=0;"+
      "for(const x of keys){const y=x.toLowerCase();if(a.toLowerCase()===y)score+=100;if(title.toLowerCase()===y)score+=80;if(t.toLowerCase()===y)score+=70;if(hay.includes(y))score+=10;}"+
      "if((e.getAttribute('role')==='button'||e.tagName==='BUTTON')&&score>0)score+=20;if(score>best){best=score;hit=e;}}"+
      "if(!hit)return JSON.stringify({ok:false,message:'لم يتم العثور على زر Reply ظاهر'});"+
      "hit.scrollIntoView({block:'center',inline:'center'});hit.click();"+
      "return JSON.stringify({ok:true,stage:'reply_clicked',score:best,message:'تم الضغط على أول Reply. جاري تجهيز خانة الرد.',replyText:replyText});})()";
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

    String buildClickJs(String kind){
      String[] keys=kind.equals("reply")?new String[]{"reply","replies","رد","الرد","الردود","رد على","عرض الردود"}:new String[]{"like","likes","إعجاب","اعجاب","أعجبني"};
      String arr=new JSONArray(java.util.Arrays.asList(keys)).toString(),k=kind;
      return "(()=>{const keys="+arr+";const norm=s=>(s||'').replace(/\\s+/g,' ').trim();const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];let hit=null,best=-1;" +
      "for(const e of els){const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),title=norm(e.getAttribute('title')),q=e.getBoundingClientRect();if(q.width<=0||q.height<=0)continue;const hay=(t+' '+a+' '+title).toLowerCase();let score=0;" +
      "for(const x of keys){if(a.toLowerCase()===x.toLowerCase())score+=100;if(title.toLowerCase()===x.toLowerCase())score+=80;if(t.toLowerCase()===x.toLowerCase())score+=70;if(hay.includes(x.toLowerCase()))score+=10;}if((e.getAttribute('role')==='button'||e.tagName==='BUTTON')&&score>0)score+=20;if(score>best){best=score;hit=e;}}" +
      "if(!hit)return JSON.stringify({ok:false,clicked:false,kind:'"+k+"',message:'لم يتم العثور على زر "+k+" قابل للنقر'});hit.scrollIntoView({block:'center',inline:'center'});const before=norm(hit.innerText)+' | '+norm(hit.getAttribute('aria-label'))+' | '+norm(hit.getAttribute('aria-pressed'));hit.click();" +
      "return JSON.stringify({ok:true,clicked:true,kind:'"+k+"',tag:hit.tagName,text:norm(hit.innerText),aria:norm(hit.getAttribute('aria-label')),role:norm(hit.getAttribute('role')),before:before,score:best,message:'تم النقر تلقائيًا على أول زر مطابق. تم تسجيل الحالة قبل النقر.'});})()";
    }


    String buildFillReplyJs(String replyText){
      String jsText=org.json.JSONObject.quote(replyText);
      return "(()=>{const replyText="+jsText+";const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"+
      "const boxes=[...document.querySelectorAll('[contenteditable=\"true\"],textarea,input[placeholder]')];let box=null;"+
      "for(const e of boxes){const r=e.getBoundingClientRect();if(r.width<=0||r.height<=0)continue;const p=norm(e.getAttribute('placeholder')).toLowerCase();const a=norm(e.getAttribute('aria-label')).toLowerCase();"+
      "if(p.includes('comment')||p.includes('reply')||p.includes('تعليق')||p.includes('رد')||a.includes('comment')||a.includes('reply')||a.includes('تعليق')||a.includes('رد')){box=e;break;}if(!box)box=e;}"+
      "if(!box)return JSON.stringify({ok:false,message:'تم فتح Reply لكن لم يتم العثور على خانة كتابة الرد'});"+
      "box.focus();if(box.isContentEditable){box.innerHTML='';document.execCommand('insertText',false,replyText);}else{const setter=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(box),'value')?.set;if(setter)setter.call(box,replyText);else box.value=replyText;}"+
      "box.dispatchEvent(new Event('input',{bubbles:true}));box.dispatchEvent(new Event('change',{bubbles:true}));"+
      "setTimeout(()=>{const btns=[...document.querySelectorAll('button,[role=button]')];let send=null;for(const e of btns){const r=e.getBoundingClientRect();if(r.width<=0||r.height<=0)continue;const t=(norm(e.innerText)+' '+norm(e.getAttribute('aria-label'))+' '+norm(e.getAttribute('title'))).toLowerCase();if(t==='send'||t.includes('send')||t.includes('إرسال')||t.includes('post')){send=e;break;}}if(send)send.click();},250);"+
      "return JSON.stringify({ok:true,message:'تمت كتابة الرد وإرساله إن ظهر زر الإرسال'});})()";
    }
    static String unquote(String s){if(s==null)return "";if(s.startsWith("\"")&&s.endsWith("\"")){try{return new org.json.JSONTokener(s).nextValue().toString();}catch(Exception ignored){}}return s;}
    @Override protected void onDestroy(){stopRepeatedDetection();super.onDestroy();}
}
