package myschedule.web.servlet;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Process Http requests using a map of action paths to some callback handlers. Subclass may setup this handler 
 * mappings, and concentrate on each action into smaller piece of code, instead of worry about the inner working of 
 * Http request processing.
 * 
 * <p>
 * The default action path will be parse from the Http request's URI after the 'servletPath' section. This action path
 * is use to lookup pre-registered handler instance to handle the request. A typical usage is that an application will
 * create one 'MainServlet' that subclass this Servlet, and declare it in the web.xml like this:
 * <pre>
 * {@code
 * <servlet>
 *     <servlet-name>MainServlet</servlet-name>
 *     <servlet-class>myschedule.web.servlet.app.MainServlet</servlet-class>
 *     <load-on-startup>1</load-on-startup>
 * </servlet>
 * <servlet-mapping>
 *     <servlet-name>MainServlet</servlet-name>
 *     <url-pattern>/main/*</url-pattern>
 * </servlet-mapping>
 * }
 * </pre>
 * 
 * Then this 'MainServlet' may setup handler mappings in the init() method. These action mappings are correspond to http 
 * URL on browser such as: <code>http://localhost/mywebapp/main-servlet/myaction</code>.
 *  
 * <p>An MainServlet example:
 * <pre>
 * public MyMainServlet extends ActionHandlerServlet {
 *   public void init() {
 *       addActionHandler("/", new ViewDataActionHandler()); // Eg: maps to http://localhost/mywebapp/main-servlet
 *       addActionHandler("/myaction", testAction);          // Eg: maps to http://localhost/mywebapp/main-servlet/myaction
 *   }
 *   protected ActionHandler testAction = new ViewDataActionHandler() {
 *       protected void handleViewData(ViewData viewData) {
 *         viewData.addData("message", "ServerTime=" + new java.util.Date());
 *       }            
 *   };
 * }
 * </pre>
 *  
 * @author Zemian Deng <saltnlight5@gmail.com>
 *
 */
public abstract class ActionHandlerServlet extends AbstractControllerServlet {
	private static final long serialVersionUID = 1L;
	private Map<String, ActionHandler> actionHandlerMappings = new HashMap<String, ActionHandler>();
	private Map<String, ActionFilter> actionFilterMappings = new HashMap<String, ActionFilter>();
		
	/** Allow subclass to add URL action path to a handler. This should be called in init() method of subclass. */
	protected void addActionHandler(String actionPath, ActionHandler handler) {
		actionPath = trimActionPath(actionPath);
		
		// We will try our best to print most useful mapping path, but it will depend where subclass
		// is adding the handler. For example, if they add it in init(), then all these are good, but 
		// if it's added in other methods, then servlet context will not be available.
		if (logger.isInfoEnabled()) {
			String ctxName = getServletContext().getContextPath();
			String fullActionPath = ctxName + getServletPathName();
			fullActionPath += actionPath;
			logger.info("Path '{}' is mapped to action handler: {}", fullActionPath, handler);
		}
		actionHandlerMappings.put(actionPath, handler);
	}
	
	protected void addActionFilter(String actionPath, ActionFilter filter) {
		actionPath = trimActionPath(actionPath);
		logger.info("Adding filter on action path starting with: {}", actionPath);
		actionFilterMappings.put(actionPath, filter);
	}
	
	/** Ensure action path does not end with '/', else remove it. */
	private String trimActionPath(String actionPath) {
		while (actionPath.endsWith("/")) {
			actionPath = actionPath.substring(0, actionPath.length() -1 );
		}
		return actionPath;
	}
	
	@Override
	protected ViewData process(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		String actionPath = getActionPath(req);
		actionPath = trimActionPath(actionPath);
		logger.debug("Action path: {}", actionPath);
		
		ActionHandler handler = findActionHandler(actionPath, req);
		logger.debug("Action handler: {}", handler);
		if (handler == null) {
			String actionServletPath = req.getServletPath()  + actionPath;
			throw new RuntimeException("Unable to find action handler for path: " + actionServletPath);
		}
		
		ActionFilter filter = findActionFilter(actionPath, req);
		if (filter != null) {
			ViewData viewData = filter.beforeAction(actionPath, req, resp);
			if (viewData != null) {
				logger.debug("Filter has stopped the before action path: {}.", actionPath);
				return viewData;
			}
		}
		
		ViewData viewData = handler.handleAction(actionPath, req, resp);
		logger.trace("Handler result: {}", viewData);
		
		if (filter != null) {
			filter.afterAction(viewData, actionPath, req, resp);
		}
		return viewData;
	}
	
	/** Extract action Path from request URI after the servletPath portion. */
	protected String getActionPath(HttpServletRequest req) {
		String contextPath = req.getContextPath();
		String servletPath = req.getServletPath();
		String reqUri = req.getRequestURI();
		return reqUri.substring(contextPath.length() + servletPath.length());
	}

	/** 
	 * Find action handler by first exact match to actionPath, else if not found then any path that match from
	 * the beginning.
	 */
	protected ActionHandler findActionHandler(String actionPath, HttpServletRequest req) {
		ActionHandler handler = actionHandlerMappings.get(actionPath);
		if (handler == null) {
			// Try to find by matching beginning of actionPath
			for (String name : actionHandlerMappings.keySet()) {
				if (actionPath.startsWith(name)) {
					handler = actionHandlerMappings.get(name);
					break;
				}
			}
		}
		return handler;
	}
	
	/** 
	 * Find action filter by first exact match to actionPath, else if not found then any path that match from
	 * the beginning.
	 */
	protected ActionFilter findActionFilter(String actionPath, HttpServletRequest req) {
		ActionFilter filter = actionFilterMappings.get(actionPath);
		if (filter == null) {
			// Try to find by matching beginning of actionPath
			for (String name : actionFilterMappings.keySet()) {
				if (actionPath.startsWith(name)) {
					filter = actionFilterMappings.get(name);
					break;
				}
			}
		}
		return filter;
	}

}
