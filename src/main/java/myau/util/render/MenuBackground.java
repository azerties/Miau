package myau.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class MenuBackground {

  private static final String SHADER_FBM =
      "#version 120\n"
          + "uniform float time;\n"
          + "uniform vec2 resolution;\n"
          + "float random(vec2 st){return fract(sin(dot(st.xy,vec2(12.9898,78.233)))*43758.5453123);}\n"
          + "float noise(vec2 st){vec2 i=floor(st);vec2 f=fract(st);float a=random(i);float b=random(i+vec2(1,0));float c=random(i+vec2(0,1));float d=random(i+vec2(1,1));vec2 u=f*f*(3.-2.*f);return mix(a,b,u.x)+(c-a)*u.y*(1.-u.x)+(d-b)*u.x*u.y;}\n"
          + "float fbm(vec2 st){float v=0.;float a=0.5;mat2 rot=mat2(cos(.5),sin(.5),-sin(.5),cos(.5));for(int i=0;i<5;++i){v+=a*noise(st);st=rot*st*2.+vec2(100);a*=.5;}return v;}\n"
          + "void main(){\n"
          + "  vec2 st=gl_FragCoord.xy/resolution*3.;st.x*=resolution.x/resolution.y;\n"
          + "  vec2 q=vec2(fbm(st),fbm(st+vec2(1)));\n"
          + "  vec2 r=vec2(fbm(st+q+vec2(1.7,9.2)+.15*time),fbm(st+q+vec2(8.3,2.8)+.126*time));\n"
          + "  float f=fbm(st+r);\n"
          + "  vec3 col=mix(vec3(.102,.62,.667),vec3(.667,.667,.498),clamp(f*f*4.,0.,1.));\n"
          + "  col=mix(col,vec3(0,0,.165),clamp(length(q),0.,1.));\n"
          + "  col=mix(col,vec3(.667,1,1),clamp(length(r.x),0.,1.));\n"
          + "  col*=.6;col=pow(col,vec3(1.2));\n"
          + "  gl_FragColor=vec4(col,1);\n"
          + "}";

  private static final String SHADER_RAYMARCHING =
      "#version 120\n"
          + "uniform vec2 resolution;\n"
          + "uniform float time;\n"
          + "mat2 m(float a){float c=cos(a),s=sin(a);return mat2(c,-s,s,c);}\n"
          + "float map(vec3 p){p.xz*=m(time*.4);p.xy*=m(time*.1);vec3 q=p*2.+time;return length(p+vec3(sin(time*.7)))*log(length(p)+1.)+sin(q.x+sin(q.z+sin(q.y)))*.5-1.;}\n"
          + "void main(){vec2 a=gl_FragCoord.xy/resolution.y-vec2(.9,.5);vec3 cl=vec3(0);float d=2.5;for(int i=0;i<=5;i++){vec3 p=vec3(0,0,4)+normalize(vec3(a,-1))*d;float rz=map(p);float f=clamp((rz-map(p+.1))*.5,-.1,1.);vec3 l=vec3(.1,.3,.4)+vec3(5,2.5,3)*f;cl=cl*l+smoothstep(2.5,0.,rz)*.6*l;d+=min(rz,1.);}gl_FragColor=vec4(cl,1);}";

  private static final String SHADER_TUNNEL =
      "#version 120\n"
          + "uniform float time;\n"
          + "uniform vec2 resolution;\n"
          + "#define PI 3.14\n"
          + "mat2 rot(float a){return mat2(cos(a),-sin(a),sin(a),cos(a));}\n"
          + "void main(){vec2 p=(gl_FragCoord.xy*2.-resolution)/min(resolution.x,resolution.y);p=rot(time*.94*PI)*p;float t;if(sin(time)==1.)t=.075/abs(1.-length(p));else t=.075/abs(.8-length(p));gl_FragColor=vec4(vec3(t)*vec3(.13*(sin(time)+12.),p.y*1.7,3.5),1);}";

  private static final String SHADER_AURORA =
      "#version 120\n"
          + "uniform float time;\n"
          + "uniform vec2 resolution;\n"
          + "void main(){\n"
          + "  vec2 uv=gl_FragCoord.xy/resolution;\n"
          + "  float t=time*.3;\n"
          + "  vec3 col=vec3(0);\n"
          + "  for(int i=0;i<4;i++){\n"
          + "    float fi=float(i);\n"
          + "    float wave=sin(uv.x*3.+t+fi*1.3)*0.5+0.5;\n"
          + "    float band=smoothstep(.0,.15,uv.y-wave*.4+fi*.12)*smoothstep(.35,0.2,uv.y-wave*.4+fi*.12);\n"
          + "    vec3 bandCol=mix(vec3(0.,.8,.6),vec3(.1,.3,.9),fi/4.+sin(t+fi)*.3);\n"
          + "    col+=bandCol*band*.5;\n"
          + "  }\n"
          + "  col+=vec3(.02,.03,.06);\n"
          + "  col*=1.-0.5*dot(uv-.5,uv-.5);\n"
          + "  gl_FragColor=vec4(col,1);\n"
          + "}";

  private static final String SHADER_SMOKE =
      "#version 120\n"
          + "uniform float time;\n"
          + "uniform vec2 resolution;\n"
          + "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}\n"
          + "float noise(vec2 p){vec2 i=floor(p);vec2 f=fract(p);vec2 u=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),u.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),u.x),u.y);}\n"
          + "void main(){\n"
          + "  vec2 uv=gl_FragCoord.xy/resolution;\n"
          + "  float t=time*.12;\n"
          + "  float n=0.;\n"
          + "  float amp=.5;vec2 freq=vec2(2);\n"
          + "  for(int i=0;i<5;i++){n+=noise(uv*freq+vec2(0,t))*amp;freq*=2.1;amp*=.5;}\n"
          + "  vec3 dark=vec3(.04,.04,.08);\n"
          + "  vec3 mid=vec3(.08,.12,.2);\n"
          + "  vec3 bright=vec3(.15,.25,.4);\n"
          + "  vec3 col=mix(dark,mid,n);\n"
          + "  col=mix(col,bright,n*n);\n"
          + "  gl_FragColor=vec4(col,1);\n"
          + "}";

  private static final String[][] SHADERS = {
    {"FBM", SHADER_FBM},
    {"Raymarching", SHADER_RAYMARCHING},
    {"Tunnel", SHADER_TUNNEL},
    {"Aurora", SHADER_AURORA},
    {"Smoke", SHADER_SMOKE}
  };

  public static final String[] NAMES;

  static {
    NAMES = new String[SHADERS.length];
    for (int i = 0; i < SHADERS.length; i++) NAMES[i] = SHADERS[i][0];
  }

  private static ShaderUtil current;
  private static int currentIndex = -1;
  private static long initTime = System.currentTimeMillis();

  public static void reload(int index) {
    if (index == currentIndex && current != null) return;
    currentIndex = index;
    current = new ShaderUtil(SHADERS[index][1]);
    initTime = System.currentTimeMillis();
  }

  public static void draw(int screenW, int screenH, int shaderIndex) {
    reload(shaderIndex);
    Minecraft mc = Minecraft.getMinecraft();

    if (current != null && current.getProgramID() != 0) {
      GlStateManager.disableCull();
      GlStateManager.disableAlpha();
      current.init();
      float t = (System.currentTimeMillis() - initTime) / 1000f;
      current.setUniformf("time", t);
      current.setUniformf("resolution", (float) mc.displayWidth, (float) mc.displayHeight);

      Tessellator tess = Tessellator.getInstance();
      WorldRenderer wr = tess.getWorldRenderer();
      wr.begin(7, DefaultVertexFormats.POSITION);
      wr.pos(0, screenH, 0).endVertex();
      wr.pos(screenW, screenH, 0).endVertex();
      wr.pos(screenW, 0, 0).endVertex();
      wr.pos(0, 0, 0).endVertex();
      tess.draw();

      current.unload();
      GlStateManager.enableAlpha();
      GlStateManager.enableCull();
    } else {
      fallback(screenW, screenH);
    }
  }

  private static void fallback(int w, int h) {
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    Tessellator tess = Tessellator.getInstance();
    WorldRenderer wr = tess.getWorldRenderer();
    wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    wr.pos(w, 0, 0).color(14, 14, 22, 255).endVertex();
    wr.pos(0, 0, 0).color(14, 14, 22, 255).endVertex();
    wr.pos(0, h, 0).color(5, 5, 12, 255).endVertex();
    wr.pos(w, h, 0).color(5, 5, 12, 255).endVertex();
    tess.draw();
    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
  }
}
