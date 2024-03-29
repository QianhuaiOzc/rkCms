package com.rekoe.cms.controller;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.nutz.dao.Cnd;
import org.nutz.dao.util.cri.SqlExpressionGroup;
import org.nutz.ioc.annotation.InjectName;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.json.Json;
import org.nutz.mvc.View;
import org.nutz.mvc.annotation.AdaptBy;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.Fail;
import org.nutz.mvc.annotation.Ok;
import org.nutz.mvc.annotation.Param;
import org.nutz.mvc.upload.TempFile;
import org.nutz.mvc.upload.UploadAdaptor;
import org.nutz.mvc.view.JspView;

import com.rekoe.cms.model.Article;
import com.rekoe.cms.model.ArticleFile;
import com.rekoe.cms.model.NavModel;
import com.rekoe.cms.model.PageModel;
import com.rekoe.cms.model.Templete;
import com.rekoe.cms.utils.CheckFileType;
import com.rekoe.cms.utils.FileUtils;
import com.rekoe.cms.utils.SystemContext;

/**
 * 文章处理Action
 * 
 * @author Shine
 * 
 *         修改： 1.去掉了原来插入另外一个系统的多于代码 2011-02-12 09:47 2.去掉了设置文章颜色的多于判断
 */
@IocBean
@InjectName
public class ArticleAction extends BaseAction {
	/**
	 * 添加文章数据
	 * 
	 * @param ioc
	 * @param a
	 * @param articlefiles
	 * @return
	 * @throws IOException
	 */
	@At("/admin/article/add")
	public void add(@Param("::a.") Article a,
			@Param("articlefile") String[] articlefiles,
			@Param("navid") int[] ids, @Param("color") String color,
			HttpServletResponse resp) throws IOException {

		// 设置标题颜色
		a.setColor(color);

		List<NavModel> list = basicDao.searchByIds(NavModel.class, ids, "id");

		a.setNav(list);

		boolean flag = false;

		List<ArticleFile> files = new ArrayList<ArticleFile>();

		for (String str : articlefiles) {
			ArticleFile f = new ArticleFile();
			f.setFilePath(str);
			f.setName(str);
			if (CheckFileType.isPic(str)) {
				f.setType(ArticleFile.PICTURE_FILE);
				f.setFilePath("pic/" + f.getFilePath());
			} else {
				f.setType(ArticleFile.DOWN_FILE);
				f.setFilePath("down/" + f.getFilePath());
			}
			files.add(f);
		}
		a.setFiles(files);
		if (a.getId() == 0) {
			if (a.getCreateDate() == null) {
				a.setCreateDate(new Date());
			}
			if (files.size() > 0) {
				a = basicDao.saveWidth(a, "files");
			} else {
				a = basicDao.save(a);
			}
			// 保存ManyMany数据
			basicDao.saveRelation(a, "nav");
			flag = true;

		} else {
			a.setModifyDate(new Date());
			if (files.size() > 0) {
				basicDao.saveLink(a, "files");
				a = basicDao.updateWidth(a, "files");
				flag = true;
			} else {
				flag = basicDao.update(a);
			}
			// 删除关联表中的数据
			basicDao.clearRelation(a, "nav");
			// 保存ManyMany数据
			a = basicDao.saveRelation(a, "nav");
		}

		resp.setContentType("text/html");
		if (flag) {
			resp.getWriter().print("[{success:true}," + Json.toJson(a) + "]");
		} else {
			resp.getWriter().print("[{success:false}]");
		}

	}

	/**
	 * 分页查询页面数据
	 * 
	 * @param currentPage
	 * @param ioc
	 */
	@At("/admin/article")
	@Ok("jsp:admin.article")
	public void list(@Param("currentPage") int currentPage,
			HttpServletRequest req) {
		// ArticleDao basicDao = new ArticleDao(ioc);

		List<Article> list = basicDao.searchByPage(Article.class, Cnd.orderBy()
				.desc("createDate"), currentPage, SystemContext.PAGE_SIZE);

		int count = basicDao.searchCount(Article.class);

		int maxPage = basicDao.maxPageSize(count, SystemContext.PAGE_SIZE);

		PageModel<Article> pm = new PageModel<Article>(list, maxPage);
		req.setAttribute("navid", 0);
		req.setAttribute("pm", pm);
	}

	@At("/admin/article/searchby")
	@Ok("jsp:admin.article")
	public void searchby(@Param("currentPage") int currentPage,
			@Param("navid") int navid, @Param("pubtime") String pubtime,
			@Param("keyword") String keyword, HttpServletRequest req)
			throws UnsupportedEncodingException {
		// ArticleDao basicDao = new ArticleDao(ioc);
		SqlExpressionGroup group = Cnd.exps("1", "=", "1");

		if (pubtime != null && !pubtime.equals("")) {
			group = group.and("createDate", "like", pubtime + "%");
			req.setAttribute("pubtime", pubtime);
		}
		if (keyword != null && !keyword.equals("")) {
			keyword = new String(keyword.getBytes("ISO-8859-1"), "UTF-8");
			group = group.and("keywords", "like", "%" + keyword + "%");
			req.setAttribute("keyword", keyword);
		}
		List<Article> list = null;
		int count = 0;
		if (navid == 0) {
			list = basicDao.searchByPage(Article.class,
					Cnd.where(group).desc("createDate"), currentPage,
					SystemContext.PAGE_SIZE);
			count = basicDao.searchCount(Article.class,
					Cnd.where(group).desc("createDate"));
		} else {
			list = basicDao.searchByRelation(Article.class, "t_article_nav",
					"article_id", Cnd.where("navmodel_id", "=", navid), group,
					"createDate", currentPage, SystemContext.PAGE_SIZE);
			count = basicDao.searchCount(Article.class, "t_article_nav",
					"article_id", Cnd.where("navmodel_id", "=", navid), group,
					"createDate");
		}

		int maxPage = basicDao.maxPageSize(count, SystemContext.PAGE_SIZE);

		PageModel<Article> pm = new PageModel<Article>(list, maxPage);
		req.setAttribute("navid", navid);

		req.setAttribute("pm", pm);
	}

	/**
	 * 上传附件
	 * 
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@At("/admin/article/upload")
	@AdaptBy(type = UploadAdaptor.class)
	public void upload(@Param("..") Map<String, Object> allFields,
			HttpServletResponse resp, ServletContext context)
			throws IOException {

		String prefix = context.getRealPath("/");
		String path = prefix + "article/";

		StringBuilder sb = new StringBuilder("{file:'");

		String p = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

		for (Entry<String, Object> entry : allFields.entrySet()) {
			if (entry.getValue() instanceof List<?>) {

				List<TempFile> files = (List<TempFile>) (entry.getValue());

				for (TempFile tf : files) {
					File file = tf.getFile();
					if (CheckFileType.isPic(file.getName())) {
						// 图片文件存放在 article/pic 目录下

						FileUtils.moveFile(file,
								path + "pic/" + p + file.getName());
					} else {
						// 下载文件存放在article/down目录下
						FileUtils.moveFile(file,
								path + "down/" + p + file.getName());
					}
					sb.append(p + file.getName());
					sb.append(",");
				}

			} else if (entry.getValue() instanceof TempFile) {
				TempFile tf = (TempFile) entry.getValue();

				File file = tf.getFile();

				if (CheckFileType.isPic(file.getName())) {
					// 图片文件存放在 article/pic 目录下
					FileUtils
							.moveFile(file, path + "pic/" + p + file.getName());
				} else {
					// 下载文件存放在article/down目录下
					FileUtils.moveFile(file,
							path + "down/" + p + file.getName());
				}

				sb.append(p + file.getName());
				sb.append(",");
			}
		}

		String str = sb.toString();

		int dot = str.lastIndexOf(",");
		if (dot != -1) {
			str = str.substring(0, dot);
		}
		str += "'}";

		resp.setContentType("text/html");
		resp.getWriter().print(str);

	}

	/**
	 * 根据id查找数据
	 * 
	 * @param id
	 * @param ioc
	 * @return
	 */
	@At("/admin/article/find")
	@Ok("json")
	public String find(@Param("id") int id) {

		// ArticleDao basicDao = new ArticleDao(ioc);

		Article a = basicDao.find(id, Article.class);

		a = basicDao.findLink(a, "nav");

		StringBuffer sb = new StringBuffer();
		if (a != null) {
			sb.append(Json.toJson(a));
		} else {
			sb.append("{id:0}");
		}
		System.out.println(sb.toString());
		return sb.toString();
	}

	/**
	 * 根据id删除数据信息
	 * 
	 * @param id
	 * @param ioc
	 * @return
	 */
	@At("/admin/article/del")
	@Ok("json")
	public String del(@Param("id") int id,
			@Param("currentPage") int currentPage, ServletContext context) {

		// ArticleDao basicDao = new ArticleDao(ioc);

		int count = basicDao.searchCount(Article.class);
		int maxPage = basicDao.maxPageSize(count, SystemContext.PAGE_SIZE);

		StringBuffer sb = new StringBuffer("[");
		if (maxPage > 1) {
			List<Article> as = basicDao.searchByPage(Article.class,
					(currentPage + 1), SystemContext.PAGE_SIZE, "id");
			for (Article a : as) {
				sb.append(Json.toJson(a));
				// 第一次执行完就停止执行
				break;
			}
		}
		sb.append("]");

		Article a = basicDao.find(id, Article.class);
		if (a != null) {

			String path = context.getRealPath("/") + "article/";

			a = basicDao.findLink(a, "files");
			List<ArticleFile> files = a.getFiles();
			for (ArticleFile file : files) {
				String spath = path + file.getFilePath();
				FileUtils.deleteFile(spath); // 删除关联的文件
			}

			basicDao.deleteLink(a, "files");
			basicDao.clearRelation(a, "nav");
		}

		if (basicDao.delById(id, Article.class)) {
			return sb.toString();
		} else {
			return "[]";
		}
	}

	/**
	 * 根据ids删除数据信息
	 * 
	 * @param ids
	 * @param ioc
	 * @return
	 */
	@At("/admin/article/delByIds")
	@Ok("json")
	public String delByIds(@Param("ids") String ids,
			@Param("currentPage") int currentPage, @Param("size") int size,
			ServletContext context) {

		// ArticleDao basicDao = new ArticleDao(ioc);

		int count = basicDao.searchCount(Article.class);
		int maxPage = basicDao.maxPageSize(count, SystemContext.PAGE_SIZE);
		String str = "";
		StringBuffer sb = new StringBuffer("[");
		if (maxPage > 1) {
			List<Article> as = basicDao.searchByPage(Article.class,
					(currentPage + 1), SystemContext.PAGE_SIZE, "id");
			int i = 0;
			for (Article a : as) {
				if (i == size) {
					break;
				} else {
					sb.append(Json.toJson(a));
					sb.append(",");
					i++;
				}
			}
		}
		str = sb.toString();
		int dot = str.lastIndexOf(",");
		if (dot != -1) {
			str = str.substring(0, dot);
		}

		List<Article> articles = basicDao.searchByIds(Article.class, ids, "id");
		String path = context.getRealPath("/") + "article/";

		for (Article a : articles) {
			a = basicDao.findLink(a, "files");
			List<ArticleFile> files = a.getFiles();
			for (ArticleFile file : files) {
				String spath = path + file.getFilePath();
				FileUtils.deleteFile(spath); // 删除关联的文件
			}
			basicDao.deleteLink(a, "files");
			basicDao.clearRelation(a, "nav");
		}

		basicDao.deleteByIds(Article.class, ids);

		return str + "]";
	}

	/**
	 * 前台搜索
	 * 
	 * @param ioc
	 * @param currentPage
	 * @param title
	 * @return
	 */
	@At("/article/search")
	@Fail("jsp:error.404")
	public View findlike(@Param("currentPage") int currentPage,
			@Param("title") String title, HttpServletRequest req) {
		// ArticleDao basicDao = new ArticleDao(ioc);

		int count = basicDao.searchByPageLike(Article.class, "title", title);
		int maxpage = basicDao.maxPageSize(count, SystemContext.PAGE_SIZE);

		List<Article> articles = basicDao.searchByPageLike(Article.class,
				"title", title, currentPage, SystemContext.PAGE_SIZE);

		req.setAttribute("maxPage", maxpage);
		req.setAttribute("articles", articles);

		return new JspView("upload.search");
	}

	/**
	 * 查询文章详细信息
	 * 
	 * @param nav
	 * @param article
	 * @param ioc
	 * @param req
	 * @return
	 */
	@At("/article/detail")
	@Fail("jsp:error.404")
	public View findArticle(@Param("nav") int nav,
			@Param("article") int article, @Param("model") int model,
			HttpServletRequest req) {

		// ArticleDao basicDao = new ArticleDao(ioc);

		// NavModelDao basicDao = new NavModelDao(ioc);

//		Cnd e = Cnd.where("id", "=", article);
		Cnd group = Cnd.where("id", "=", article).and("show", "=", 1);

		Article a = basicDao.findByCondition(Article.class, group);

		req.setAttribute("model", basicDao.find(model, NavModel.class));
		req.setAttribute("navsec", nav);

		if (a != null) {
			// 查询指定文章
			req.setAttribute("article", a);
			String title = a.getTitle();
			req.setAttribute("title", title);
			basicDao.findLink(a, "style");
			Templete t = a.getStyle();
			String path = t.getFilePath();
			int dot = path.lastIndexOf(".");
			if (dot != -1) {
				path = path.substring(0, dot);
			}

			if (title != null && !"".equals(title.trim())) {

				int len = title.length() - 4;
				if (len > 0) {
					title = title.substring(len, title.length());
				}
			}

			// 查询相关文章
			List<Article> articles = basicDao.searchPageByLike(Article.class,
					title, "createDate", 1, 6);
			req.setAttribute("articles", articles);

			// 增加点击率
			a.setClick(a.getClick() + 1);
			basicDao.update(a);

			return new JspView(path);
		}

		return new JspView("error.404");
	}
}
