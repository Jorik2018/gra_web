package org.isobit.app.jsf;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;
import javax.inject.Named;
import javax.servlet.http.Cookie;
import org.isobit.app.X;
import org.isobit.app.ejb.UserFacadeLocal;
import org.isobit.app.jpa.User;
import org.isobit.directory.jpa.People;
import org.isobit.util.AbstractController;
import org.isobit.util.OptionMap;
import org.isobit.util.XUtil;

@Named
@RequestScoped
public class UserController extends AbstractController<User> implements Serializable {
  public boolean access(Object p) {
    return this.ejbFacade.access(p);
  }

  private int id = 0;

  private static int c = 0;

  private static final Map ESTADO_MAP = (Map) new OptionMap();

  static {
    ESTADO_MAP.put(Short.valueOf((short) 0), "BLOQUEADO");
    ESTADO_MAP.put(Short.valueOf((short) 1), "ACTIVO");
    ESTADO_MAP.put(Short.valueOf((short) -1), "PENDIENTE");
  }

  public Map getESTADO_MAP() {
    return ESTADO_MAP;
  }

  public boolean initRegister() {
    People pn = (People) getParams().get("people");
    if (pn == null) {
      getParams().put("people", new People());
      getParams().put("form", new HashMap<>());
    }
    return true;
  }

  protected void persist(X.PersistAction persistAction, String successMessage) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  private static final Map EXTENSION_MAP = new HashMap<>();

  @EJB
  private UserFacadeLocal ejbFacade;

  public void setExtension(Object k) {
    getParams().put("xc", getExtension(k));
  }

  public Object getExtension(Object k) {
    Object[] ext = (Object[]) getEXTENSION_MAP().get(k);
    return !k.equals("D") ? FacesContext.getCurrentInstance().getApplication().getELResolver()
        .getValue(FacesContext.getCurrentInstance().getELContext(), null, ext[1]) : this;
  }

  static {
    EXTENSION_MAP.put("D", new Object[] { "DRUPAL", "userController" });
    EXTENSION_MAP.put("P", new Object[] { "PEGASUS", "usuarioController" });
    EXTENSION_MAP.put("S", new Object[] { "SIIGAA", "pspUsuarioController" });
  }

  public Map getEXTENSION_MAP() {
    return EXTENSION_MAP;
  }

  public void setDni(String dni) {
    setSelected(this.ejbFacade.getByDni(dni));
    if (getSelected() != null) {
      getParams().put("dni", dni);
    } else {
      getParams().put("dni", "");
    }
  }

  public String getDni() {
    return (String) getParams().get("dni");
  }

  private UserFacadeLocal getFacade() {
    return this.ejbFacade;
  }

  public User getUser(Integer id) {
    return getFacade().find(id);
  }

  public User getUserByDir(int idDir) {
    return getFacade().getUserByDir(idDir);
  }

  public String logout() throws IOException {
    System.out.println("userControler.logout");
    getFacade().logout();
    FacesContext context = FacesContext.getCurrentInstance();
    ExternalContext external = context.getExternalContext();
    Map<String, String> paramMap = context.getExternalContext().getRequestParameterMap();
    String destiny = paramMap.get("destiny");
    HttpServletResponse response = (HttpServletResponse) external.getResponse();
    Cookie refreshCookie = new Cookie("refreshToken", "");
    refreshCookie.setPath("/");
    refreshCookie.setMaxAge(0);
    refreshCookie.setHttpOnly(true);
    refreshCookie.setSecure(true);
    response.addCookie(refreshCookie);
    if (destiny != null && !destiny.trim().isEmpty()) {
      external.redirect(destiny);
    } else {
      external.redirect("/login");
    }
    context.responseComplete();
    return null;
  }

  public void validateDni(FacesContext fc, UIComponent uic, Object value) throws ValidatorException {
    if (this.ejbFacade.existsNameXorMail(value.toString(), null))
      throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, "Dni ya existe",
          "Ya existe una cuenta vinculada a este mail determine una distinta o si tiene acceso a este correo trate <a href=\""
              +
              X.getRequest().getContextPath()
              + "/faces/password.xhtml\">solicitar nueva contraseseleccionando este enlace</a>"));
  }

  public void validate(FacesContext fc, UIComponent uic, Object value) throws ValidatorException {
    if (this.ejbFacade.existsNameXorMail(null, value.toString()))
      throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR, "Mail ya existe",
          "Ya existe una cuenta vinculada a este mail determine una distinta o si tiene acceso a este correo trate <a href=\""
              +
              X.getRequest().getContextPath()
              + "/faces/password.xhtml\">solicitar nueva contraseseleccionando este enlace</a>"));
  }

  public void send(Map m) {
    try {
      this.ejbFacade.send(m);
      X.alert("" + m.get("body"));
    } catch (Exception e) {
      X.alert(e);
    }
  }

  public User prepareCreate() {
    User u = (User) super.prepareCreate();
    HashMap<Object, Object> m = new HashMap<>();
    m.put("SADAD", Integer.valueOf(4444));
    u.setExt(m);
    return u;
  }

  public void preRenderView(Map<String, Integer> m) {
    String[] q = (String[]) X.getRequest().getAttribute("#q");
    m.put("uid", Integer.valueOf(XUtil.intValue(q[2])));
    m.put("timestamp", Integer.valueOf(XUtil.intValue(q[3])));
    m.put("hashedPass", q[4]);
    passReset(m);
  }

  public String passReset(Map m) {
    try {
      X.getRequest().setAttribute(X.NO_LOAD, Boolean.valueOf(true));
      Object result = this.ejbFacade.passReset(XUtil.intValue(m.get("uid")), XUtil.intValue(m.get("timestamp")),
          (String) m.get("hashedPass"), m);
      if (result != null) {
        Object destiny = X.getSession().getAttribute("_DESTINY");
        X.getSession().removeAttribute("_DESTINY");
        String d = (XUtil.isEmpty(destiny) ? "admin" : destiny).toString();
        X.getViewParam().redirect("/" + d);
      }
    } catch (Exception e) {
      X.alert(e);
    }
    return null;
  }

  public static interface UserControllerExt {
    User prepareCreate(User param1User);

    User prepareEdit(UserController param1UserController);

    void save(User param1User);
  }
}
