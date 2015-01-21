package com.afd.img.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import org.apache.commons.lang.StringUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.im4java.core.Info;
import org.im4java.core.InfoException;
import org.im4java.process.ProcessStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.afd.common.exception.TechException;
import com.afd.common.generator.UUIDGenerator;
import com.afd.img.config.ConfigFactory;
import com.afd.img.core.AbstractOp.CutOffOp;
import com.afd.img.core.AbstractOp.OpHelper;
import com.afd.img.core.AbstractOp.ResizeOp;
import com.afd.model.img.ImgResource;

/**
 * 
 * 图片资源助手
 * 
 * @author xuzunyuan
 * @date 2015年1月16日
 */
public class ImgHelper {
	private static final Logger logger = LoggerFactory
			.getLogger(ImgHelper.class);

	private static final String PREFIX = "IMG";
	private static final char ID_SPLITTER = '_';
	private static final char PATH_SPLITTER = '/';
	private static ConvertCmd CMD = null;

	public static final String IMG_TYPE = "image"; // image图片类型

	static {
		System.setProperty("im4java.useGM", "true");
		if (ConfigFactory.getConfig().getGlobalSearchPath() != null) {
			ProcessStarter.setGlobalSearchPath(ConfigFactory.getConfig()
					.getGlobalSearchPath());
		}
	}

	private ImgHelper() {
	}

	private static ConvertCmd getConvertCmd() {
		if (CMD == null) {
			createCovertCmd();
		}

		return CMD;
	}

	private synchronized static void createCovertCmd() {
		if (CMD == null) {
			CMD = new ConvertCmd();
		}
	}

	/**
	 * 创建资源ID
	 * 
	 * @param fileExt
	 * @return
	 */
	private static String generateResourceId(String fileExt) {
		Calendar calendar = Calendar.getInstance();

		int year = calendar.get(Calendar.YEAR);
		int month = calendar.get(Calendar.MONTH);
		int day = calendar.get(Calendar.DATE);

		return PREFIX + ID_SPLITTER + year + ID_SPLITTER
				+ StringUtils.leftPad(String.valueOf(++month), 2, '0')
				+ ID_SPLITTER
				+ StringUtils.leftPad(String.valueOf(day), 2, '0')
				+ ID_SPLITTER + UUIDGenerator.getUUID32() + "." + fileExt;
	}

	/**
	 * 从资源ID中解析文件全路径
	 * 
	 * @param resourceId
	 * @return
	 */
	private static String getFileNameFromResourceId(String resourceId) {
		String fileSavePath = ConfigFactory.getConfig().getFileSavePath();

		return fileSavePath
				+ (fileSavePath.endsWith(String.valueOf(PATH_SPLITTER)) ? ""
						: PATH_SPLITTER) + resourceId.substring(4, 8)
				+ PATH_SPLITTER + resourceId.substring(9, 11) + PATH_SPLITTER
				+ resourceId.substring(12, 14) + PATH_SPLITTER + resourceId;
	}

	private static String getFileNameFromResourceIdAndOp(String resourceId,
			String op) {
		String fileSavePath = ConfigFactory.getConfig().getFileSavePath();
		int index = resourceId.lastIndexOf('.');

		return fileSavePath
				+ (fileSavePath.endsWith(String.valueOf(PATH_SPLITTER)) ? ""
						: PATH_SPLITTER) + resourceId.substring(4, 8)
				+ PATH_SPLITTER + resourceId.substring(9, 11) + PATH_SPLITTER
				+ resourceId.substring(12, 14) + PATH_SPLITTER
				+ resourceId.substring(0, index) + "$" + op
				+ resourceId.substring(index);
	}

	/**
	 * 从资源ID中解析mime type
	 * 
	 * @param id
	 * @return
	 */
	public static String getContentTypeFromResourceId(String resourceId) {
		return IMG_TYPE + "/"
				+ resourceId.substring(resourceId.indexOf(".") + 1);
	}

	/**
	 * 保存图片文件
	 * 
	 * @param fileName
	 * @param fileExt
	 * @param in
	 * @return
	 */
	public static ImgResource saveImg(String fileName, String fileExt,
			InputStream in) {

		String id = generateResourceId(fileExt);
		String pathName = getFileNameFromResourceId(id);

		File file = new File(pathName);
		File dir = file.getParentFile();
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				return null;
			}
		}
		try {
			if (!file.createNewFile()) {
				return null;
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}

		FileOutputStream out = null;

		try {
			out = new FileOutputStream(file);

			int b = 0;
			byte[] buffer = new byte[4096];

			while ((b = in.read(buffer)) != -1) {
				out.write(buffer, 0, b);
				out.flush();
			}

			// 获取图片信息
			try {
				Info info = new Info(pathName);

				ImgResource resource = new ImgResource();

				resource.setRid(id);
				resource.setWidth(info.getImageWidth());
				resource.setHeight(info.getImageHeight());
				resource.setType(info.getImageFormat());

				return resource;

			} catch (InfoException e) {
				logger.error(e.getMessage(), e);
				return null;
			}

		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
			return null;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
				}
		}
	}

	/**
	 * 获取图片输入流
	 * 
	 * @param id
	 * @return
	 */
	public static InputStream getImg(String id, String op) {
		FileInputStream in = null;
		String srcPathName = getFileNameFromResourceId(id);

		if (StringUtils.isEmpty(op)) {
			File file = new File(srcPathName);
			try {
				in = new FileInputStream(file);

			} catch (FileNotFoundException e) {
				logger.error(e.getMessage(), e);
			}

		} else {
			String pathName = getFileNameFromResourceIdAndOp(id, op);

			File file = new File(pathName);
			if (!file.exists()) {
				// 生成缩略图
				createAbbrImg(srcPathName, pathName, op);
			}

			try {
				in = new FileInputStream(file);

			} catch (FileNotFoundException e) {
				logger.error(e.getMessage(), e);
			}
		}

		return in;
	}

	/**
	 * 创建缩略图
	 * 
	 * @param srcPathName
	 * @param destPathName
	 * @param op
	 */
	private static final void createAbbrImg(String srcPathName,
			String destPathName, String ops) {

		AbstractOp[] opArray = OpHelper.parseOps(ops);

		for (AbstractOp op : opArray) {
			IMOperation im = new IMOperation();
			im.addImage();

			if (op instanceof ResizeOp) {
				ResizeOp resizeOp = (ResizeOp) op;

				if (resizeOp.w == null || resizeOp.w == 0 || resizeOp.h == null
						|| resizeOp.h == 0) {
					throw new TechException("incorrect op");
				}

				String special = "";

				if (resizeOp.type == 1) { // 等比最小缩放
					special += "^";

				} else if (resizeOp.type == 2) { // 不等比缩放
					special += "!";
				}

				if (resizeOp.e == null || resizeOp.e != 1) {
					special += ">";
				}

				im.resize(resizeOp.w, resizeOp.h, special);

			} else {
				CutOffOp cutoffOp = (CutOffOp) op;
				ImgSize imgSize = null;

				switch (op.type) {
				case 1:
					// 宽度裁剪
					if (cutoffOp.w == null || cutoffOp.w == 0) {
						throw new TechException("incorrect op");
					}

					imgSize = getImgSize(srcPathName);
					if (imgSize == null)
						throw new TechException("unable to get img size");

					im.crop(cutoffOp.w, imgSize.height);

					break;

				case 2:
					// 高度裁剪
					if (cutoffOp.h == null || cutoffOp.h == 0) {
						throw new TechException("incorrect op");
					}

					imgSize = getImgSize(srcPathName);
					if (imgSize == null)
						throw new TechException("unable to get img size");

					im.crop(imgSize.width, cutoffOp.h);

					break;

				case 3:
					// 中部裁剪
					if (cutoffOp.w == null || cutoffOp.w == 0
							|| cutoffOp.h == null || cutoffOp.h == 0) {
						throw new TechException("incorrect op");
					}

					imgSize = getImgSize(srcPathName);
					if (imgSize == null)
						throw new TechException("unable to get img size");

					im.crop(cutoffOp.w, cutoffOp.h,
							(imgSize.width - cutoffOp.w) / 2,
							(imgSize.height - cutoffOp.h) / 2);

					break;

				case 4:
					// 高度等分裁剪
					if (cutoffOp.p == null || cutoffOp.p == 0) {
						throw new TechException("incorrect op");
					}

					imgSize = getImgSize(srcPathName);
					if (imgSize == null)
						throw new TechException("unable to get img size");

					im.crop(imgSize.width, imgSize.height / cutoffOp.p, 0,
							(imgSize.height / cutoffOp.p) * cutoffOp.a);

					break;

				default:
					// 普通裁剪
					if (cutoffOp.w == null || cutoffOp.w == 0
							|| cutoffOp.h == null || cutoffOp.h == 0) {
						throw new TechException("incorrect op");
					}

					im.crop(cutoffOp.w, cutoffOp.h, cutoffOp.x, cutoffOp.y);
				}

			}

			im.addImage();

			try {
				getConvertCmd().run(im, srcPathName, destPathName);

				// if there are more than one operates, continue to deal with
				// the destination file
				srcPathName = destPathName;

			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
				break;
			}
		}

	}

	/**
	 * 获取图片尺寸
	 * 
	 * @param filePathName
	 * @return
	 */
	private static ImgSize getImgSize(String filePathName) {
		ImgSize ret = new ImgSize();
		Info info = null;

		try {
			info = new Info(filePathName);

			ret.width = info.getImageWidth();
			ret.height = info.getImageHeight();

		} catch (InfoException e) {
			logger.error(e.getMessage(), e);
			return null;
		}

		return ret;
	}

	/**
	 * 获取图片格式信息
	 * 
	 * @param id
	 * @return
	 */
	public static final ImgResource getImageInfo(String id) {
		String filePathName = getFileNameFromResourceId(id);

		ImgResource resource = null;

		Info info = null;

		try {
			info = new Info(filePathName);

			resource = new ImgResource();
			resource.setRid(id);
			resource.setWidth(info.getImageWidth());
			resource.setHeight(info.getImageHeight());
			resource.setType(info.getImageFormat());

		} catch (InfoException e) {
			logger.error(e.getMessage(), e);
		}

		return resource;
	}
}
