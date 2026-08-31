package org.ocpsoft.rewrite;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.isobit.app.X;
import org.isobit.app.ejb.MenuFacadeLocal;
import org.isobit.app.ejb.SystemFacadeLocal;
import org.isobit.app.ejb.UserFacadeLocal;
import org.isobit.app.jpa.User;
import org.isobit.util.AbstractFacadeLocal;
import org.isobit.util.TestFacadeLocal;
import org.isobit.util.XUtil;
import org.ocpsoft.common.pattern.WeightedComparator;
import org.ocpsoft.common.services.ServiceLoader;
import org.ocpsoft.common.spi.ServiceEnricher;
import org.ocpsoft.common.util.Iterators;
import org.ocpsoft.logging.Logger;
import org.ocpsoft.rewrite.config.ConfigurationProvider;
import org.ocpsoft.rewrite.el.spi.ExpressionLanguageProvider;
import org.ocpsoft.rewrite.event.Flow;
import org.ocpsoft.rewrite.event.Rewrite;
import org.ocpsoft.rewrite.servlet.RewriteFilter;
import org.ocpsoft.rewrite.servlet.ServletRewriteProvider;
import org.ocpsoft.rewrite.servlet.event.BaseRewrite;
import org.ocpsoft.rewrite.servlet.event.InboundServletRewrite;
import org.ocpsoft.rewrite.servlet.impl.HttpRewriteContextImpl;
import org.ocpsoft.rewrite.servlet.spi.ContextListener;
import org.ocpsoft.rewrite.servlet.spi.InboundRewriteProducer;
import org.ocpsoft.rewrite.servlet.spi.OutboundRewriteProducer;
import org.ocpsoft.rewrite.servlet.spi.RequestCycleWrapper;
import org.ocpsoft.rewrite.servlet.spi.RequestListener;
import org.ocpsoft.rewrite.servlet.spi.RequestParameterProvider;
import org.ocpsoft.rewrite.servlet.spi.RewriteLifecycleListener;
import org.ocpsoft.rewrite.servlet.spi.RewriteResultHandler;
import org.ocpsoft.rewrite.spi.ConfigurationCacheProvider;
import org.ocpsoft.rewrite.spi.InvocationResultHandler;
import org.ocpsoft.rewrite.spi.RewriteProvider;
import org.ocpsoft.rewrite.util.ServiceLogger;

public class RewriteFilter implements Filter {
  private static String DEFAULT_TEMPLATE = null;
  
  static {
    try {
      Class.forName("org.primefaces.util.Constants");
      DEFAULT_TEMPLATE = "/template.xhtml";
    } catch (Exception e) {
      DEFAULT_TEMPLATE = "/template-vue.xhtml";
    } 
  }
  
  private static Logger log = Logger.getLogger(RewriteFilter.class);
  
  private static String FILTER_COUNT_KEY = RewriteFilter.class.getName() + "_FILTER_COUNT";
  
  private List<RewriteLifecycleListener<Rewrite>> listeners;
  
  private List<RequestCycleWrapper<ServletRequest, ServletResponse>> wrappers;
  
  private List<RewriteProvider<ServletContext, Rewrite>> providers;
  
  private List<RewriteResultHandler> resultHandlers;
  
  private List<InboundRewriteProducer<ServletRequest, ServletResponse>> inbound;
  
  private List<OutboundRewriteProducer<ServletRequest, ServletResponse, Object>> outbound;
  
  private ServletContext servletContext;
  
  public void init(FilterConfig filterConfig) throws ServletException {
    if (log.isInfoEnabled())
      log.info("RewriteFilter starting up..."); 
    this.servletContext = filterConfig.getServletContext();
    this.listeners = Iterators.asList((Iterable)ServiceLoader.load(RewriteLifecycleListener.class));
    this.wrappers = Iterators.asList((Iterable)ServiceLoader.load(RequestCycleWrapper.class));
    this.providers = Iterators.asList((Iterable)ServiceLoader.load(RewriteProvider.class));
    this.resultHandlers = Iterators.asList((Iterable)ServiceLoader.load(RewriteResultHandler.class));
    this.inbound = Iterators.asList((Iterable)ServiceLoader.load(InboundRewriteProducer.class));
    this.outbound = Iterators.asList((Iterable)ServiceLoader.load(OutboundRewriteProducer.class));
    Collections.sort(this.listeners, (Comparator<? super RewriteLifecycleListener<Rewrite>>)new WeightedComparator());
    Collections.sort(this.wrappers, (Comparator<? super RequestCycleWrapper<ServletRequest, ServletResponse>>)new WeightedComparator());
    Collections.sort(this.providers, (Comparator<? super RewriteProvider<ServletContext, Rewrite>>)new WeightedComparator());
    Collections.sort(this.resultHandlers, (Comparator<? super RewriteResultHandler>)new WeightedComparator());
    Collections.sort(this.inbound, (Comparator<? super InboundRewriteProducer<ServletRequest, ServletResponse>>)new WeightedComparator());
    Collections.sort(this.outbound, (Comparator<? super OutboundRewriteProducer<ServletRequest, ServletResponse, Object>>)new WeightedComparator());
    ServiceLogger.logLoadedServices(log, RewriteLifecycleListener.class, this.listeners);
    ServiceLogger.logLoadedServices(log, RequestCycleWrapper.class, this.wrappers);
    ServiceLogger.logLoadedServices(log, RewriteProvider.class, this.providers);
    ServiceLogger.logLoadedServices(log, RewriteResultHandler.class, this.resultHandlers);
    ServiceLogger.logLoadedServices(log, InboundRewriteProducer.class, this.inbound);
    ServiceLogger.logLoadedServices(log, OutboundRewriteProducer.class, this.outbound);
    ServiceLogger.logLoadedServices(log, ContextListener.class, 
        Iterators.asList((Iterable)ServiceLoader.load(ContextListener.class)));
    ServiceLogger.logLoadedServices(log, RequestListener.class, 
        Iterators.asList((Iterable)ServiceLoader.load(RequestListener.class)));
    ServiceLogger.logLoadedServices(log, RequestParameterProvider.class, 
        Iterators.asList((Iterable)ServiceLoader.load(RequestParameterProvider.class)));
    ServiceLogger.logLoadedServices(log, ExpressionLanguageProvider.class, 
        Iterators.asList((Iterable)ServiceLoader.load(ExpressionLanguageProvider.class)));
    ServiceLogger.logLoadedServices(log, InvocationResultHandler.class, 
        Iterators.asList((Iterable)ServiceLoader.load(InvocationResultHandler.class)));
    ServiceLogger.logLoadedServices(log, ServiceEnricher.class, 
        Iterators.asList((Iterable)ServiceLoader.load(ServiceEnricher.class)));
    ServiceLogger.logLoadedServices(log, ConfigurationCacheProvider.class, 
        Iterators.asList((Iterable)ServiceLoader.load(ConfigurationCacheProvider.class)));
    List<ConfigurationProvider<?>> configurations = Iterators.asList(
        (Iterable)ServiceLoader.load(ConfigurationProvider.class));
    ServiceLogger.logLoadedServices(log, ConfigurationProvider.class, configurations);
    for (RewriteProvider<ServletContext, Rewrite> provider : this.providers) {
      if (provider instanceof ServletRewriteProvider)
        ((ServletRewriteProvider)provider).init(this.servletContext); 
    } 
    if ((configurations == null || configurations.isEmpty()) && 
      log.isWarnEnabled())
      log.warn("No ConfigurationProviders were registered: Rewrite will not be enabled on this application. Did you forget to create a '/META-INF/services/" + ConfigurationProvider.class
          
          .getName() + " file containing the fully qualified name of your provider implementation?"); 
    if (log.isInfoEnabled())
      log.info(Version.getFullName() + " initialized."); 
  }
  
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (!preFilter(request, response, chain))
      return; 
    if (request.getAttribute("noload") != null)
      chain.doFilter(request, response); 
    InboundServletRewrite<ServletRequest, ServletResponse> event = createRewriteEvent(request, response);
    if (event == null) {
      if (log.isWarnEnabled())
        log.warn("No Rewrite event was produced - RewriteFilter disabled on this request."); 
      chain.doFilter(request, response);
    } else {
      incrementFilterCount(request);
      if (request.getAttribute("_com.ocpsoft.rewrite.RequestContext") == null) {
        HttpRewriteContextImpl httpRewriteContextImpl = new HttpRewriteContextImpl(this.inbound, this.outbound, this.listeners, this.resultHandlers, this.wrappers, this.providers);
        request.setAttribute("_com.ocpsoft.rewrite.RequestContext", httpRewriteContextImpl);
      } 
      for (RewriteLifecycleListener<Rewrite> listener : this.listeners) {
        if (listener.handles(event))
          listener.beforeInboundLifecycle((Rewrite)event); 
      } 
      for (RequestCycleWrapper<ServletRequest, ServletResponse> wrapper : this.wrappers) {
        if (wrapper.handles(event)) {
          event.setRequest(wrapper.wrapRequest(event.getRequest(), event.getResponse(), this.servletContext));
          event.setResponse(wrapper.wrapResponse(event.getRequest(), event.getResponse(), this.servletContext));
        } 
      } 
      try {
        rewrite(event);
      } catch (ServletException e) {
        if (getFilterCount(request) == 1)
          AbstractRewrite.logEvaluatedRules((Rewrite)event, Logger.Level.ERROR); 
        decrementFilterCount(request);
        throw e;
      } catch (RuntimeException e) {
        if (getFilterCount(request) == 1)
          AbstractRewrite.logEvaluatedRules((Rewrite)event, Logger.Level.ERROR); 
        decrementFilterCount(request);
        throw e;
      } 
      if (!event.getFlow().is((Flow)BaseRewrite.ServletRewriteFlow.ABORT_REQUEST)) {
        if (log.isDebugEnabled())
          log.debug("RewriteFilter passing control of request to underlying application."); 
        if (response.isCommitted() && log.isWarnEnabled())
          log.warn("Response has already been committed, and further write operations are not permitted. This may result in an IllegalStateException being triggered by the underlying application. To avoid this situation, consider adding a Rule `.when(Direction.isInbound().and(Response.isCommitted())).perform(Lifecycle.abort())`, or figure out where the response is being incorrectly committed and correct the bug in the offending code."); 
        chain.doFilter(event.getRequest(), event.getResponse());
        if (log.isDebugEnabled())
          log.debug("Control of request returned to RewriteFilter."); 
      } 
      for (RewriteLifecycleListener<Rewrite> listener : this.listeners) {
        if (listener.handles(event))
          listener.afterInboundLifecycle((Rewrite)event); 
      } 
      if (getFilterCount(request) == 1)
        AbstractRewrite.logEvaluatedRules((Rewrite)event, Logger.Level.DEBUG); 
      decrementFilterCount(request);
    } 
  }
  
  public boolean preFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    try {
      HttpServletRequest request = (HttpServletRequest)req;
      HttpServletResponse response = (HttpServletResponse)res;
      if (request != null) {
        HttpSession session = request.getSession(false);
        String requestURI = request.getRequestURI();
        int p = requestURI.indexOf(";jsessionid");
        if (p > -1)
          requestURI = requestURI.substring(0, p); 
        String contextPath = request.getContextPath();
        if (X.CONTEXT_PATH == null)
          X.CONTEXT_PATH = contextPath; 
        request.setAttribute("contextPath", contextPath);
        if (requestURI.endsWith("/"))
          requestURI = requestURI.substring(0, requestURI.length() - 1); 
        if (requestURI.startsWith("/"))
          requestURI = requestURI.substring(1); 
        String[] q = requestURI.split("/");
        X.log("Q=>" + X.gson.toJson(q));
        q[q.length - 1] = q[q.length - 1].replaceAll(".xhtml", "");
        if (request.getAttribute("#q") == null) {
          request.setAttribute("#q", q);
          request.setAttribute("#requestURI", requestURI);
        } 
        boolean isLocalhost = request.getRequestURL().toString().startsWith("http://localhost");
        if (session == null)
          session = request.getSession(true); 
        X.setSession(session);
        X.setRequest(request);
        request.setAttribute("_MSG", X.getSession().getAttribute("_MSG"));
        X.getSession().removeAttribute("_MSG");
        String URI = requestURI.toLowerCase();
        if (URI.contains("javax.faces.resource") || URI
          .endsWith(".jpg") || URI
          .endsWith(".js") || URI
          .endsWith(".ttf") || URI
          .endsWith(".properties") || URI
          .endsWith(".png") || URI
          .endsWith(".gif") || URI
          .endsWith(".png") || URI
          .endsWith(".css") || URI
          .endsWith(".png") || URI
          .endsWith(".svg") || URI
          .endsWith(".ico")) {
          req.setAttribute(X.NO_LOAD, Boolean.valueOf(true));
          String destinyRequest = req.getParameter("destiny");
          if (destinyRequest != null)
            request.getSession().setAttribute("_DESTINY", destinyRequest); 
          chain.doFilter(req, (ServletResponse)response);
          return false;
        } 
        String logout = req.getParameter("action");
        if ("logout".equals(logout)) {
          ((UserFacadeLocal)(new InitialContext()).lookup("java:module/UserFacade")).logout();
          response.sendRedirect("/" + requestURI);
          return false;
        } 
        if (req.getAttribute(X.NO_LOAD) != null) {
          chain.doFilter(req, (ServletResponse)response);
          return false;
        } 
        if (request.getAttribute("URL_ENTER") == null && req.getAttribute(X.NO_LOAD) == null)
          request.setAttribute("URL_ENTER", requestURI); 
        if (!X.installed) {
          SystemFacadeLocal systemFacade = lookupSystemFacadeLocal();
          Map m = systemFacade.getConfig("SYSTEM");
          if (m == null || 
            !m.containsKey("installed") || 
            !(X.installed = systemFacade.hasAdmin())) {
            if (!requestURI.endsWith("faces/Install.xhtml")) {
              response.sendRedirect("/faces/Install.xhtml");
              return false;
            } 
            try {
              System.out.println("2do chain.doFilter(req, response);");
            } catch (Exception e) {
              e.printStackTrace();
            } 
          } 
        } 
        if (isLocalhost && session != null && session.getAttribute("_USER") == null)
          try {
            ((TestFacadeLocal)((AbstractFacadeLocal)(new InitialContext()).lookup("java:module/PeopleFacadeLocalImpl")).getModule(TestFacadeLocal.class)).init(session);
          } catch (Exception e) {
            X.log(e);
          }  
        if (requestURI.contains("/api/") || "api"
          .equals(q[0]) || (q.length > 1 && "api".equals(q[1]))) {
          response.addHeader("Access-Control-Allow-Origin", "*");
          response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD, PUT, POST");
          chain.doFilter(req, (ServletResponse)response);
          return false;
        } 
        if (request.getAttribute("TEMPLATE") == null)
          if (request.getParameter("modal") != null) {
            request.setAttribute(X.TEMPLATE, "/modal.xhtml");
          } else {
            request.setAttribute(X.TEMPLATE, (q.length > 0 && "admin"
                .equals(q[0])) ? DEFAULT_TEMPLATE : "/nodeTemplate.xhtml");
          }  
        if (isLocalhost || requestURI
          .endsWith("/login.xhtml") || requestURI
          .endsWith("/register") || requestURI
          .startsWith("user/reset/") || requestURI
          .endsWith("/password") || session
          
          .getAttribute("_USER") != null || !requestURI.startsWith("admin")) {
          X.DEBUG = true;
          String destinyRequest = req.getParameter("destiny");
          if (!XUtil.isEmpty(destinyRequest) && requestURI.endsWith("/login.xhtml")) {
            User user = (User)session.getAttribute("_USER");
            if (user != null && user.getUid().intValue() > 0) {
              ((UserFacadeLocal)(new InitialContext()).lookup("java:module/UserFacade")).initSession(user.getUid());
              response.sendRedirect("/" + destinyRequest + "?access_token=" + user.getUid());
              return true;
            } 
          } 
          if (destinyRequest != null)
            request.getSession().setAttribute("_DESTINY", destinyRequest); 
          if (req.getAttribute("noload") != null)
            return true; 
          if (requestURI.startsWith("admin") || requestURI.startsWith("faces/")) {
            String access_token = req.getParameter("access_token");
            if (access_token != null) {
              Object modal = req.getParameter("modal");
              if (modal != null)
                requestURI = requestURI + "?modal"; 
              if (session.getAttribute("_USER") == null) {
                Object uid = req.getParameter("uid");
                if (uid != null) {
                  Map m = validateToken(access_token);
                  if (XUtil.booleanValue(m.get("valid"))) {
                    ((UserFacadeLocal)(new InitialContext()).lookup("java:module/UserFacade")).initSession(Integer.valueOf(XUtil.intValue(uid)));
                    response.sendRedirect("/" + requestURI);
                    return false;
                  } 
                } else {
                  ((UserFacadeLocal)(new InitialContext()).lookup("java:module/UserFacade")).initSession(Integer.valueOf(XUtil.intValue(access_token)));
                  response.sendRedirect("/" + requestURI);
                  return false;
                } 
              } 
              response.sendRedirect("/" + requestURI);
              return false;
            } 
            if (req.getAttribute("-checkedAccess") == null) {
              Object o = null;
              try {
                o = ((MenuFacadeLocal)(new InitialContext()).lookup("java:module/MenuFacade")).accessMenu(q);
              } catch (Exception e) {
                e.printStackTrace();
              } 
              if (o instanceof Exception) {
                ((Exception)o).printStackTrace();
                request.setAttribute("MSG", ((Exception)o).getMessage());
                req.setAttribute("noload", Boolean.valueOf(true));
                request.getRequestDispatcher("/faces/common/Page.xhtml").forward(req, res);
              } 
              req.setAttribute("-checkedAccess", Boolean.valueOf(true));
            } 
          } else if (requestURI.endsWith(".xhtml")) {
            req.setAttribute("noload", Boolean.valueOf(true));
          } else {
            X.log("Se desactiva autentificacion para los redireccionamiento despues de '" + requestURI + "'; solo se solicitan login a url entrantes que empiezen con admin o faces.");
            req.setAttribute("-checkedAccess", Boolean.valueOf(true));
          } 
        } else if (req.getAttribute("-checkedAccess") == null) {
          System.out.println("Despues de -checkedAccess " + requestURI);
          if ("faces/".equals(requestURI))
            requestURI = null; 
          String access_token = req.getParameter("access_token");
          User user = (User)session.getAttribute("_USER");
          if (access_token != null && user == null) {
            System.out.println("session=" + session);
            System.out.println("session=" + session);
            System.out.println("Se inicia session para user=" + access_token);
            String[] tt = access_token.split("[.]");
            if (tt.length == 1) {
              user = ((UserFacadeLocal)(new InitialContext()).lookup("java:module/UserFacade")).initSession(Integer.valueOf(XUtil.intValue(access_token)));
              if (user != null) {
                System.out.println("USER INICIADO=" + session.getAttribute("_USER") + " Despues se redirige a /" + requestURI);
                session.setAttribute("_USER", user);
                response.sendRedirect("/" + requestURI);
                return false;
              } 
            } else if (X.toText(X.getClientIpAddr(X.getRequest())).equals(access_token.split(".")[0])) {
              user = ((UserFacadeLocal)(new InitialContext()).lookup("java:module/UserFacade")).initSessionByToken(access_token);
              if (user != null) {
                session.setAttribute("_USER", user);
                response.sendRedirect("/" + requestURI);
                return false;
              } 
            } 
          } 
          session.setAttribute("_DESTINY", requestURI);
          System.out.println("response.sendRedirect " + user + " - " + session.getAttribute("_USER") + " - /faces/login.xhtml?destiny=" + requestURI);
          response.sendRedirect("/faces/login.xhtml?destiny=" + requestURI);
          return false;
        } 
      } 
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } 
    return true;
  }
  
  private Map validateToken(String token) throws Exception {
    String POST_PARAMS = "{\"token\":\"" + token + "\"}";
    System.out.println(POST_PARAMS);
    URL obj = new URL("http://web.regionancash.gob.pe/auth/api/tblusuario/verificartoken");
    HttpURLConnection postConnection = (HttpURLConnection)obj.openConnection();
    postConnection.setRequestMethod("POST");
    postConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    postConnection.setDoOutput(true);
    OutputStream os = postConnection.getOutputStream();
    os.write(POST_PARAMS.getBytes());
    os.flush();
    os.close();
    int responseCode = postConnection.getResponseCode();
    BufferedReader in = new BufferedReader(new InputStreamReader(postConnection.getInputStream(), Charset.forName("UTF-8")));
    StringBuffer response = new StringBuffer();
    String inputLine;
    while ((inputLine = in.readLine()) != null)
      response.append(inputLine); 
    in.close();
    System.out.println(response);
    HashMap<Object, Object> m = new HashMap<>();
    JsonParser parser = Json.createParser(new ByteArrayInputStream(response.toString().getBytes()));
    String keyName = "";
    while (parser.hasNext()) {
      JsonParser.Event e = parser.next();
      switch (null.$SwitchMap$javax$json$stream$JsonParser$Event[e.ordinal()]) {
        case 1:
          m.put(keyName, Integer.valueOf(parser.getInt()));
        case 2:
          m.put(keyName, parser.getString());
        case 3:
          m.put(keyName, Boolean.valueOf(true));
        case 4:
          m.put(keyName, Boolean.valueOf(false));
        case 5:
          keyName = parser.getString();
          switch (keyName) {
            case "valido":
              keyName = "valid";
          } 
      } 
    } 
    return m;
  }
  
  public InboundServletRewrite<ServletRequest, ServletResponse> createRewriteEvent(ServletRequest request, ServletResponse response) {
    for (InboundRewriteProducer<ServletRequest, ServletResponse> producer : this.inbound) {
      InboundServletRewrite<ServletRequest, ServletResponse> event = producer.createInboundRewrite(request, response, this.servletContext);
      if (event != null)
        return event; 
    } 
    return null;
  }
  
  private void rewrite(InboundServletRewrite<ServletRequest, ServletResponse> event) throws ServletException, IOException {
    int listenerCount = this.listeners.size();
    for (int i = 0; i < listenerCount; i++) {
      RewriteLifecycleListener<Rewrite> listener = this.listeners.get(i);
      if (listener.handles(event))
        listener.beforeInboundRewrite((Rewrite)event); 
    } 
    int providerCount = this.providers.size();
    int j;
    for (j = 0; j < providerCount; j++) {
      RewriteProvider<ServletContext, Rewrite> provider = this.providers.get(j);
      if (provider.handles(event)) {
        provider.rewrite((Rewrite)event);
        if (event.getFlow().is((Flow)BaseRewrite.ServletRewriteFlow.HANDLED)) {
          if (log.isDebugEnabled())
            log.debug("Event flow marked as HANDLED. No further processing will occur."); 
          break;
        } 
      } 
    } 
    for (j = 0; j < listenerCount; j++) {
      RewriteLifecycleListener<Rewrite> listener = this.listeners.get(j);
      if (listener.handles(event))
        listener.afterInboundRewrite((Rewrite)event); 
    } 
    int handlerCount = this.resultHandlers.size();
    for (int k = 0; k < handlerCount; k++) {
      if (((RewriteResultHandler)this.resultHandlers.get(k)).handles(event))
        ((RewriteResultHandler)this.resultHandlers.get(k)).handleResult((Rewrite)event); 
    } 
  }
  
  public void destroy() {
    log.info("RewriteFilter shutting down...");
    for (RewriteProvider<ServletContext, Rewrite> provider : this.providers) {
      if (provider instanceof ServletRewriteProvider)
        ((ServletRewriteProvider)provider).shutdown(this.servletContext); 
    } 
    log.info("RewriteFilter deactivated.");
  }
  
  private int getFilterCount(ServletRequest request) {
    return ((Integer)request.getAttribute(FILTER_COUNT_KEY)).intValue();
  }
  
  private void decrementFilterCount(ServletRequest request) {
    Integer count = (Integer)request.getAttribute(FILTER_COUNT_KEY);
    if (count != null)
      Integer integer1 = count, integer2 = count = Integer.valueOf(count.intValue() - 1); 
    request.setAttribute(FILTER_COUNT_KEY, count);
  }
  
  private void incrementFilterCount(ServletRequest request) {
    Integer count = (Integer)request.getAttribute(FILTER_COUNT_KEY);
    if (count == null) {
      count = Integer.valueOf(1);
    } else {
      Integer integer1 = count, integer2 = count = Integer.valueOf(count.intValue() + 1);
    } 
    request.setAttribute(FILTER_COUNT_KEY, count);
  }
  
  private SystemFacadeLocal lookupSystemFacadeLocal() {
    try {
      return (SystemFacadeLocal)(new InitialContext()).lookup("java:module/SystemFacade!org.isobit.app.ejb.SystemFacadeLocal");
    } catch (NamingException ne) {
      throw new RuntimeException(ne);
    } 
  }
}
