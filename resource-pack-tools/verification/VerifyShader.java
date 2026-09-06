import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
class VerifyShader {
  static Path pack;
  static ZipFile client;
  static String expand(String source) throws Exception {
    var matcher=Pattern.compile("#moj_import <([^>]+)>").matcher(source);
    var result=new StringBuilder();
    while(matcher.find()) {
      String[] id=matcher.group(1).split(":",2);
      String resource="assets/"+id[0]+"/shaders/include/"+id[1];
      String include=Files.exists(pack.resolve(resource))?Files.readString(pack.resolve(resource)):new String(client.getInputStream(client.getEntry(resource)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
      matcher.appendReplacement(result,Matcher.quoteReplacement(expand(include.replaceAll("(?m)^#version[^\\r\\n]*", ""))));
    }
    matcher.appendTail(result); return result.toString();
  }
  public static void main(String[] args) throws Exception {
    pack=Path.of(args[0]); client=new ZipFile(args[1]);
    long compiler=shaderc_compiler_initialize(),options=shaderc_compile_options_initialize();
    shaderc_compile_options_set_target_env(options,shaderc_target_env_opengl,shaderc_env_version_opengl_4_5);
    shaderc_compile_options_set_auto_bind_uniforms(options,true);
    shaderc_compile_options_set_auto_map_locations(options,true);
    for(String defines:List.of("", "IS_GUI", "IS_SEE_THROUGH", "IS_GRAYSCALE", "IS_GUI,IS_GRAYSCALE", "IS_SEE_THROUGH,IS_GRAYSCALE")) {
      for(String extension:List.of("vsh","fsh")) {
        String resource="assets/minecraft/shaders/core/text."+extension;
        String source=Files.exists(pack.resolve(resource))?Files.readString(pack.resolve(resource)):new String(client.getInputStream(client.getEntry(resource)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        String macros=defines.isEmpty()?"":"#define "+defines.replace(",","\n#define ")+"\n";
        source=expand(source).replace("#version 330","#version 330\n"+macros);
        long result=shaderc_compile_into_spv(compiler,source,extension.equals("vsh")?shaderc_vertex_shader:shaderc_fragment_shader,resource,"main",options);
        if(shaderc_result_get_compilation_status(result)!=shaderc_compilation_status_success)throw new IllegalStateException(defines+" "+extension+"\n"+shaderc_result_get_error_message(result));
        shaderc_result_release(result);System.out.println("PASS "+extension+" ["+defines+"]");
      }
    }
    shaderc_compile_options_release(options);shaderc_compiler_release(compiler);client.close();
  }
}


