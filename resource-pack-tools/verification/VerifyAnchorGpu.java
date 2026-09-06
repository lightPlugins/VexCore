import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;
class VerifyAnchorGpu {
  static Path pack;
  static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
  public static void main(String[] args) throws Exception {
    pack=Path.of(args[0]); if(!glfwInit())throw new IllegalStateException("GLFW unavailable");
    glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
    long window=glfwCreateWindow(32,32,"VexCore shader verification",0,0);
    if(window==0)throw new IllegalStateException("No hidden OpenGL context");
    try {
      glfwMakeContextCurrent(window);GL.createCapabilities();
      String include=Files.readString(pack.resolve("assets/vexcore/shaders/include/screen_ui/v26_2.glsl"));
      int shader=glCreateShader(GL_VERTEX_SHADER);
      glShaderSource(shader,"#version 330\nuniform sampler2D Sampler0;\nuniform mat4 ProjMat;\nuniform float GameTime;\nuniform vec4 Color;\nlayout(location=0) in vec4 Position;\nlayout(location=1) in vec2 UV0;\nout vec4 tested;\nout float testedAlpha;\n"+include+"\nvoid main(){ tested=vex_screen_ui_position(Position,UV0,gl_VertexID);gl_Position=tested;testedAlpha=vexAnimatedColor.a;}");
      glCompileShader(shader);if(glGetShaderi(shader,GL_COMPILE_STATUS)==0)throw new IllegalStateException(glGetShaderInfoLog(shader));
      int program=glCreateProgram();glAttachShader(program,shader);glTransformFeedbackVaryings(program,new CharSequence[]{"tested","testedAlpha"},GL_INTERLEAVED_ATTRIBS);glLinkProgram(program);
      if(glGetProgrami(program,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(program));glUseProgram(program);
      int vao=glGenVertexArrays();glBindVertexArray(vao);
      int input=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,input);glBufferData(GL_ARRAY_BUFFER,4*6*4,GL_DYNAMIC_DRAW);
      glVertexAttribPointer(0,4,GL_FLOAT,false,24,0);glEnableVertexAttribArray(0);glVertexAttribPointer(1,2,GL_FLOAT,false,24,16);glEnableVertexAttribArray(1);
      int output=glGenBuffers();glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER,output);glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER,80,GL_DYNAMIC_READ);glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER,0,output);
      int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
      int cases=0;
      for(int kind=0;kind<9;kind++) {
        boolean text = kind==0 || kind==3 || kind==5 || kind==7; int cw=text?16:256,ch=text?14:256; int dw=kind==1?512:cw,dh=kind==1?512:ch,dx=text?0:kind==1?-116:-8,dy=text?-2:kind==1?-208:-8;
        var pixels=BufferUtils.createByteBuffer(512*512*4);
        int ox=3,oy=7;
        int[] a={86,88,49,1},b={67,79,82,1};
        for(int c=0;c<4;c++){pixels.put(((oy*512)+ox)*4+c,(byte)a[c]);pixels.put((((oy+1)*512)+ox)*4+c,(byte)b[c]);}
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,512,512,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        for(int[] viewport:List.of(new int[]{1280,720,4},new int[]{854,480,1},new int[]{1920,1080,3},new int[]{1279,719,2},new int[]{3440,1440,4},new int[]{3840,2160,1})) {
          float pw=(float)viewport[0]/viewport[2],ph=(float)viewport[1]/viewport[2];int gw=(int)Math.ceil(pw),gh=(int)Math.ceil(ph);
          float[] projection={2/pw,0,0,0,0,-2/ph,0,0,0,0,1,0,0,0,0,1};glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,projection);
          for(int anchor=0;anchor<9;anchor++)for(int carrier: new int[]{3,22,98,1000}) for(int localY: new int[]{-256,-143,-135,-119,-105,0,143,256}) for(int localX: new int[]{-752,-120,8,512}) for(float age : (kind==3||kind==4||kind==7||kind==8) ? new float[]{0,0.25f,1,2.5f,4.75f,5,32,65,69,72.5f,73,74,23999} : new float[]{0}) {
            int payload=(511<<15)|23998;
            glUniform4f(glGetUniformLocation(program,"Color"),((payload>>16)&255)/255f,((payload>>8)&255)/255f,(payload&255)/255f,1);
            glUniform1f(glGetUniformLocation(program,"GameTime"),((23998+age)%24000)/24000f);
            float entrance=smooth(age/5),exit=smooth((age-65)/8);
            float alpha=(kind==3||kind==4||kind==7||kind==8)?entrance*(1-exit):-1;
            float slide=(kind==3||kind==4||kind==7||kind==8)?20*(1-entrance):0;
            int row=anchor/3;
            int code=1024+(kind*9+anchor)*513+256;
            var vertices=BufferUtils.createFloatBuffer(24);
            for(int corner=0;corner<4;corner++) {
              boolean bottom=corner==1||corner==2,right=corner==2||corner==3;
              vertices.put(new float[]{gw/2+localX+dx+localY*4096+(right?dw:0),(float)(code*4096+512)+carrier+(bottom?dh:0),0,1,
                (ox+(right?cw-0.01f:0.01f))/512,(oy+(bottom?ch-0.01f:0.01f))/512});
            }
            vertices.flip();glBindBuffer(GL_ARRAY_BUFFER,input);glBufferSubData(GL_ARRAY_BUFFER,0,vertices);
            glEnable(GL_RASTERIZER_DISCARD);glBeginTransformFeedback(GL_POINTS);glDrawArrays(GL_POINTS,0,4);glEndTransformFeedback();glDisable(GL_RASTERIZER_DISCARD);
            var result=BufferUtils.createFloatBuffer(20);glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER,output);glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER,0,result);
            for(int corner=0;corner<4;corner++) {
              float expectedX=(float)Math.floor(gw*(anchor%3)*0.5)+localX+dx+slide+((corner==2||corner==3)?dw:0);
              float expectedY=(float)Math.floor(gh*row*0.5)+localY+((corner==1||corner==2)?dh:0)+dy;
              if(kind>=5){
                float factor=Math.min(0.5f,gw/760f);
                float ax=(float)Math.floor(gw*(anchor%3)*0.5),ay=(float)Math.floor(gh*row*0.5);
                expectedX=ax+(expectedX-ax)*factor;expectedY=ay+(expectedY-ay)*factor;
              }
              if(Math.abs(result.get(corner*5+4)-alpha)>0.01)throw new IllegalStateException("Wrong alpha at age="+age+": "+result.get(corner*5+4)+" expected "+alpha);
              if(Math.abs(result.get(corner*5)-expectedX)>0.01||Math.abs(result.get(corner*5+1)-expectedY)>0.01)throw new IllegalStateException("Mismatch kind="+kind+" anchor="+anchor+" carrier="+carrier+" got="+result.get(corner*5)+","+result.get(corner*5+1)+" expected="+expectedX+","+expectedY);
            }
            cases++;
          }
        }
      }
      System.out.println("PASS "+cases+" GPU transform cases (text, panels, cards and toast fade/slide including clock wrap, all anchors and viewport scales)");
      glDeleteTextures(texture);glDeleteBuffers(input);glDeleteBuffers(output);glDeleteVertexArrays(vao);glDeleteProgram(program);glDeleteShader(shader);
    } finally {glfwDestroyWindow(window);glfwTerminate();}
  }
}

