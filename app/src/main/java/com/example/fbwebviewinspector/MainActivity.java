package com.example.fbwebviewinspector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    WebView web; TextView results,status; Button scan,testReply,testLike;

    String scanJs = "" +
      "(()=>{const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"+
      "const labels=['reply','replies','comment','like','likes','react','reaction','رد','الرد','الردود','إعجاب','اعجاب','أعجبني','عرض الردود','رد على','مشاركة'];"+
      "const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];"+
      "const out=[];for(const e of els){const t=norm(e.innerText),a=norm(e.getAttribute('aria-label')),r=norm(e.getAttribute('role')),title=norm(e.getAttribute('title'));"+
      "const hay=(t+' '+a+' '+title).toLowerCase();if(labels.some(x=>hay.includes(x.toLowerCase()))){out.push({tag:e.tagName,text:t.slice(0,120),aria:a,role:r,title:title,clickable:!!(e.onclick||e.getAttribute('tabindex')!==null||r==='button'||r==='link'),rect:(()=>{const q=e.getBoundingClientRect();return {x:Math.round(q.x),y:Math.round(q.y),w:Math.round(q.width),h:Math.round(q.height)}})()});}}"+
      "return JSON.stringify({url:location.href,title:document.title,count:out.length,items:out.slice(0,80)});})()";

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle b){
      super.onCreate(b); setContentView(R.layout.activity_main);
      web=findViewById(R.id.web); results=findViewById(R.id.results); status=findViewById(R.id.status);
      scan=findViewById(R.id.scan); testReply=findViewById(R.id.testReply); testLike=findViewById(R.id.testLike);
      WebSettings s=web.getSettings();
      s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
      s.setSupportMultipleWindows(false);
      s.setUserAgentString(s.getUserAgentString()+" FacebookWebViewInspector/1.1");
      web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){status.setText("تم تحميل الصفحة: "+u);}});
      web.loadUrl("https://www.facebook.com/");
      scan.setOnClickListener(v->runScan());
      testReply.setOnClickListener(v->runTest("reply"));
      testLike.setOnClickListener(v->runTest("like"));
    }

    void runScan(){
      status.setText("جاري فحص DOM...");
      web.evaluateJavascript(scanJs,val->{
        try{
          String json=unquote(val); JSONObject o=new JSONObject(json); JSONArray a=o.getJSONArray("items");
          StringBuilder sb=new StringBuilder();
          sb.append("URL: ").append(o.optString("url")).append("\\nعدد العناصر المطابقة: ").append(a.length()).append("\\n\\n");
          for(int i=0;i<a.length();i++){
            JSONObject x=a.getJSONObject(i);
            sb.append(i+1).append(") ").append(x.optString("text")).append(" | aria=").append(x.optString("aria"))
              .append(" | role=").append(x.optString("role")).append(" | clickable=").append(x.optBoolean("clickable")).append("\\n");
          }
          results.setText(sb.toString()); status.setText("اكتمل الفحص");
        }catch(Exception e){results.setText(val); status.setText("تعذر تحليل نتيجة الفحص");}
      });
    }

    void runTest(String kind){
      status.setText("جاري اختبار "+kind+" تلقائيًا...");
      web.evaluateJavascript(buildClickJs(kind),val->{
        try{
          JSONObject o=new JSONObject(unquote(val));
          results.setText(o.toString(2));
          status.setText(o.optString("message","انتهى الاختبار"));
        }catch(Exception e){results.setText(val); status.setText("انتهى الاختبار");}
      });
    }

    String buildClickJs(String kind){
      String[] keys=kind.equals("reply")
        ?new String[]{"reply","replies","رد","الرد","الردود","رد على","عرض الردود"}
        :new String[]{"like","likes","إعجاب","اعجاب","أعجبني"};
      String arr=new JSONArray(java.util.Arrays.asList(keys)).toString();
      String k=kind;
      return "(()=>{const keys="+arr+";const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"+
      "const els=[...document.querySelectorAll('button,[role=button],[role=link],a,[aria-label],div[tabindex]')];"+
      "let hit=null;let best=-1;for(const e of els){const text=norm(e.innerText),aria=norm(e.getAttribute('aria-label')),title=norm(e.getAttribute('title'));"+
      "const hay=(text+' '+aria+' '+title).toLowerCase();const q=e.getBoundingClientRect();if(q.width<=0||q.height<=0)continue;"+
      "let score=0;for(const x of keys){if(aria.toLowerCase()===x.toLowerCase())score+=100;if(title.toLowerCase()===x.toLowerCase())score+=80;if(text.toLowerCase()===x.toLowerCase())score+=70;if(hay.includes(x.toLowerCase()))score+=10;}"+
      "if((e.getAttribute('role')==='button'||e.tagName==='BUTTON')&&score>0)score+=20;if(score>best){best=score;hit=e;}}"+
      "if(!hit)return JSON.stringify({ok:false,clicked:false,kind:'"+k+"',message:'لم يتم العثور على زر "+k+" قابل للنقر'});"+
      "hit.scrollIntoView({block:'center',inline:'center'});const before=norm(hit.innerText)+' | '+norm(hit.getAttribute('aria-label'))+' | '+norm(hit.getAttribute('aria-pressed'));"+
      "hit.click();"+
      "return JSON.stringify({ok:true,clicked:true,kind:'"+k+"',tag:hit.tagName,text:norm(hit.innerText),aria:norm(hit.getAttribute('aria-label')),role:norm(hit.getAttribute('role')),before:before,score:best,message:'تم النقر تلقائيًا على أول زر مطابق. تم تسجيل الحالة قبل النقر.'});})()";
    }

    static String unquote(String s){
      if(s==null)return "";
      if(s.startsWith("\"")&&s.endsWith("\"")){try{return new org.json.JSONTokener(s).nextValue().toString();}catch(Exception ignored){}}
      return s;
    }
}
