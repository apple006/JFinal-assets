package net.dreamlu.kit;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.jfinal.kit.HashKit;
import com.jfinal.kit.PathKit;
import com.jfinal.kit.StrKit;
import com.jfinal.log.Log;
import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import sun.security.pkcs.EncodingException;

/**
 * YUICompressor压缩帮助类
 * @author L.cm
 */
public class AssetsKit {
	
	private static final Log log = Log.getLog(AssetsKit.class);
	private static final String CHARSET = "UTF-8";
	private static final String JS_EXT = ".js", CSS_EXT = ".css";
	private static final String PROTOCOL = "^http\\:\\/\\/.+$";

	/**
	 * 压缩css,js帮助
	 * @param fileList 合并压缩的文件列表
	 * @param isCss 是否是css
	 * @param out 输出流
	 * @throws IOException Io异常
	 */
	private static void compressorHelper(List<String> fileList, boolean isCss, Writer out) throws IOException {
		Reader in = null;
		try {
			if (isCss) {
				for (String path : fileList) {
					in = new InputStreamReader(isRomte(path)? new URL(path).openStream():new FileInputStream(PathKit.getWebRootPath() + path), CHARSET);
					if(path.indexOf(".min.") > 0 || isRomte(path)){// 对.min.css的css放弃压缩
						out.append(repairCss(IOUtils.toString(in), path));
					}else{
						CssCompressor css = new CssCompressor(new StringReader(repairCss(IOUtils.toString(in), path)));
						in.close(); in = null;
						css.compress(out, -1);
					}
				}
			}else{
				// nomunge: 混淆,verbose：显示信息消息和警告,preserveAllSemiColons：保留所有的分号 ,disableOptimizations 禁止优化
				boolean munge = true, verbose = false, preserveAllSemiColons = false, disableOptimizations = false;
				for (String path : fileList) {
					in = new InputStreamReader(isRomte(path)? new URL(path).openStream():new FileInputStream(PathKit.getWebRootPath() + path), CHARSET);
					if(path.indexOf(".min.") > 0 || isRomte(path)){ // 对.min.js的js放弃压缩
						out.append(IOUtils.toString(in));
					}else{
						JavaScriptCompressor compressor = new JavaScriptCompressor(in, new ErrorReporter() {
							public void warning(String message, String sourceName,
								  int line, String lineSource, int lineOffset) {
								if (line < 0) {
									log.error("\n[WARNING] " + message);
								} else {
									log.error("\n[WARNING] " + line + ':' + lineOffset + ':' + message);
								}
							}
							public void error(String message, String sourceName,
								  int line, String lineSource, int lineOffset) {
								if (line < 0) {
									log.error("\n[ERROR] " + message);
								} else {
									log.error("\n[ERROR] " + line + ':' + lineOffset + ':' + message);
								}
							}
							public EvaluatorException runtimeError(String message, String sourceName,
								  int line, String lineSource, int lineOffset) {
								error(message, sourceName, line, lineSource, lineOffset);
								return new EvaluatorException(message);
							}
						});
						in.close(); in = null;
						compressor.compress(out, -1, munge, verbose, preserveAllSemiColons, disableOptimizations);
					}
				}
			}
			out.flush();
		}catch(IOException e){
			throw e;
		}finally{
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(out);
		}
	}
	
	/**
	 * 将css文件里的图片相对路径修改为绝对路径
	 * @param content 内容
	 * @param path 路径
	 * @return String css
	 */
	private static String repairCss(String content, String path){
		Pattern p = Pattern.compile("url\\([\\s]*['\"]?((?!['\"]?https?://|['\"]?data:|['\"]?/).*?)['\"]?[\\s]*\\)"); // 感谢Code Life(程式人生)的正则
		Matcher m = p.matcher(content);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String url = m.group(1).trim();
			StringBuffer cssPath = new StringBuffer("url(").append(FilenameUtils.getFullPath(path)).append(url).append(")");
			m.appendReplacement(sb, cssPath.toString());
		}
		m.appendTail(sb);
		content = sb.toString();
		return content;
	}


	/**
	 * 压缩工具
	 * @param fileName 待压缩的文件列表文件 /assets/assets.jjs
	 * @return String 返回压缩完成之后的路径
	 * @throws IOException 文件不存在时异常
	 */
	public static String combo(String fileName) throws IOException {
		String rootPath = PathKit.getWebRootPath();
		// 路径判读
		if (!fileName.startsWith("/")) {
			fileName = "/" + fileName;
		}
		File assetsConfig = new File(rootPath + fileName);
		// 待压缩的文件列表不存在时抛出异常
		if (!assetsConfig.exists()) {
			throw new IOException(fileName + " not found...");
		}
		// 读取文件中的js或者css路径
		List<String> list = FileUtils.readLines(assetsConfig, CHARSET);
		StringBuilder fileMd5s = new StringBuilder(); // 文件更改时间拼接
		for (String string : list) {
			if (StrKit.isBlank(string)) {
				continue;
			}
			// 去除首尾空格
			string = string.trim();
			// #开头的行注释 或者 远程服务器上的资源文件
			if (string.startsWith("#") || isRomte(string)) {
				continue;
			}
			// 对错误地址修复
			if (!string.startsWith("/")) {
				string = "/" + string;
			}
			String filePath = rootPath + string;
			File file = new File(filePath);
			if (!file.exists()) {
				throw new IOException(file.getName() + " not found...");
			}
			String content = FileUtils.readFileToString(file, CHARSET);
			fileMd5s.append(HashKit.md5(content));
		}
		// 文件更改时间集合hex，MD5取中间8位
		String hex = HashKit.md5(fileMd5s.toString()).substring(8, 16);
		boolean isCss = true;
		if (fileName.endsWith(".jjs")) {
			isCss = false;
		}
		// /assets/assets.jjs
		String comboName = fileName.substring(0, fileName.indexOf('.'));
		String newFileName = comboName + '-'  + hex + (isCss ? CSS_EXT : JS_EXT);
		
		String newPath = rootPath + newFileName;
		File file = new File(newPath);
		// 判断文件是否已存在，已存在直接返回
		if (file.exists()) {
			return newFileName;
		}
		// 将合并的结果写入文件，异常时将文件删除
		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(newPath), CHARSET);
			compressorHelper(list, isCss, out);
		} catch (Exception e) {
			FileUtils.deleteQuietly(file);
			throw new RuntimeException(fileName + " 压缩异常，请检查是否有依赖问题！");
		}
		return newFileName;
	}

	/**
	 * 判断文件是否为远程资源文件,远程资源文件不进行压缩
	 * @param path
	 * @return
	 */
	private static boolean isRomte(String path){
		if(StrKit.isBlank(path)){
			return false;
		}
		return path.trim().matches(PROTOCOL);

	}

}