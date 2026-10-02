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
    Button scan,testReply,testLike,stopTimer,collapseBtn;
    EditText intervalInput,commentInput;
    LinearLayout appPanel;
    FrameLayout webContainer;
    View divider;
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
      commentInput=findViewById(R.id.commentInput); appPanel=findViewById(R.id.appPanel); webContainer=findViewById(R.id.webContainer); divider=findViewById(R.id.divider); collapseBtn=findViewById(R.id.collapseBtn);

      WebSettings s=web.getSettings();
      s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
      s.setSupportMultipleWindows(false);
      s.setUserAgentString(s.getUserAgentString()+" FacebookWebViewInspector/1.2");
      web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){status.setText("تم تحميل الصفحة: "+u);}});
      web.loadUrl("https://www.facebook.com/");

      scan.setOnClickListener(v->runScan());
      testReply.setOnClickListener(v->runReplyWithText());
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

      divider.setOnTouchListener(new View.OnTouchListener(){
        float downY; int startHeight;
        public boolean onTouch(View v, android.view.MotionEvent e){
          if(e.getAction()==MotionEvent.ACTION_DOWN){ downY=e.getRawY(); startHeight=appPanel.getHeight(); return true; }
          if(e.getAction()==MotionEvent.ACTION_MOVE){
            int h=(int)(startHeight+(e.getRawY()-downY));
            int min=(int)(110*getResources().getDisplayMetrics().density);
            int max=(int)(getResources().getDisplayMetrics().heightPixels*0.70f);
            h=Math.max(min,Math.min(max,h));
            appPanel.getLayoutParams().height=h; appPanel.getLayoutParams().weight=0; appPanel.requestLayout(); return true;
          }
          if(e.getAction()==MotionEvent.ACTION_UP){
            if(Math.abs(e.getRawY()-downY)<12) setFullscreen(true);
            return true;
          }
          return true;
        }
      });
      collapseBtn.setOnClickListener(v->setFullscreen(false));
    }

    void setFullscreen(boolean full){
      if(full){
        appPanel.setVisibility(View.GONE);
        divider.setVisibility(View.GONE);
        collapseBtn.setVisibility(View.VISIBLE);
        webContainer.setLayoutParams(new FrameLayout.LayoutParams(-1,0,Gravity.BOTTOM));
      }else{
        appPanel.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);
        collapseBtn.setVisibility(View.GONE);
        webContainer.setLayoutParams(new FrameLayout.LayoutParams(-1,0,Gravity.BOTTOM));
      }
      webContainer.requestLayout();
    }

    void runReplyWithText(){
      String text=commentInput.getText().toString().trim();
      if(text.isEmpty()){
        status.setText("اكتب نص الـReply أولًا");
        commentInput.requestFocus();
        return;
      }
      status.setText("جاري البحث عن أول Reply...");
      web.evaluateJavascript(buildReplyJs(text),val->{
        try{
          JSONObject o=new JSONObject(unquote(val));
          results.setText(o.toString(2));
          status.setText(o.optString("message","انتهى Reply"));
        }catch(Exception e){ results.setText(val); status.setText("انتهى التنفيذ"); }
      });
    }

    String buildReplyJs(String replyText){
      String safe=JSONObject.quote(replyText);
      return "(()=>{const keys=['reply','replies','رد','الرد','الردود','رد على','عرض الردود'];const norm=s=>(s||'').replace(/\\s+/g,' ').trim();const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];let hit=null,best=-1;"+
      "for(const e of els){const q=e.getBoundingClientRect();if(q.width<=0||q.height<=0)continue;const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),title=norm(e.getAttribute('title')),hay=(t+' '+a+' '+title).toLowerCase();let score=0;for(const x of keys){if(a.toLowerCase()===x.toLowerCase())score+=100;if(t.toLowerCase()===x.toLowerCase())score+=80;if(title.toLowerCase()===x.toLowerCase())score+=70;if(hay.includes(x.toLowerCase()))score+=10;}if(score>best){best=score;hit=e;}}"+
      "if(!hit)return JSON.stringify({ok:false,message:'لم يتم العثور على Reply ظاهر'});hit.scrollIntoView({block:'center',inline:'center'});hit.click();setTimeout(()=>{const inputs=[...document.querySelectorAll('[contenteditable=true],textarea,input')].filter(x=>{const q=x.getBoundingClientRect();return q.width>0&&q.height>0});const box=inputs[inputs.length-1];if(box){box.focus();const val="+safe+";if(box.tagName==='TEXTAREA'||box.tagName==='INPUT'){box.value=val;box.dispatchEvent(new Event('input',{bubbles:true}));}else{document.execCommand('insertText',false,val);box.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:val}));}}},700);return JSON.stringify({ok:true,clicked:true,message:'تم الضغط على أول Reply وتجهيز خانة الرد للنص المدخل. راجع النص واضغط إرسال من Facebook.',text:"+safe+"});})()";
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
