package dev.packsmith.mt;
import bin.mt.json.JSON;
import bin.mt.json.JSONValue;
import bin.mt.json.WriterConfig;
final class JsonUtil {
 private JsonUtil(){}
 static String pretty(String s){ return JSON.parse(s).toString(WriterConfig.PRETTY_PRINT); }
 static String minify(String s){ return JSON.parse(s).toString(WriterConfig.MINIMAL); }
 static void validate(String s){ JSON.parse(s); }
}
