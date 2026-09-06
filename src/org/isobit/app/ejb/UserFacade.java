package org.isobit.app.ejb;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import org.isobit.app.X;
import org.isobit.app.jpa.Login;
import org.isobit.app.jpa.MenuRouter;
import org.isobit.app.jpa.Role;
import org.isobit.app.jpa.User;
import org.isobit.app.jpa.UserRolPK;
import org.isobit.app.jpa.UserRole;
import org.isobit.app.jsf.SystemController;
import org.isobit.directory.jpa.People;
import org.isobit.util.AbstractFacade;
import org.isobit.util.BeanUtils;
import org.isobit.util.Encrypter;
import org.isobit.util.RandomUtil;
import org.isobit.util.SimpleException;
import org.isobit.util.SystemUtilities;
import org.isobit.util.XDate;
import org.isobit.util.XMap;
import org.isobit.util.XUtil;

@Stateless
public class UserFacade extends AbstractFacade<User> implements UserFacadeLocal, BlockFacadeLocal.BlockModule, ContactFacadeLocal.ContactModule {
  @EJB
  private ContactFacadeLocal contactFacade;
  
  @EJB
  private SystemFacadeLocal systemFacade;
  
  public List getUserScopeList(User u) {
    return getEntityManager().createQuery("SELECT ue.scope FROM UserScope ue WHERE ue.uid=:uid").setParameter("uid", u.getUid()).getResultList();
  }
  
  private static List perms_anonymous = null;
  
  public static final String ACCESS_CALLBACK = "access callback";
  
  public static User anonymous;
  
  public static final List connectedUser;
  
  private static final Role AUTHENTICATED;
  
  private static final String USER_MAIL_ = "USER_MAIL_";
  
  @EJB
  private SessionFacadeLocal sessionFacade;
  
  private static String[] moduleNameList;
  
  private String[] getModuleNameList() {
    if (moduleNameList == null) {
      List l = X.getModuleList(UserFacadeLocal.UserModule.class);
      moduleNameList = (String[])l.toArray((Object[])new String[l.size()]);
    } 
    return moduleNameList;
  }
  
  public String mailText(String key, String language, Map variables) {
    String langcode = "";
    Object admin_setting = this.systemFacade.getV("USER_MAIL_" + key, false);

    if (!(admin_setting instanceof Boolean))
      return ""; 
    if (key.equals(S.REGISTER_NO_APPROVAL_REQUIRED + "_subject"))
      return t("Detalles de la cuenta de usuario de !" + T.NAME + " en !" + T.SITE + " (aprobada).", variables, langcode); 
    if (key.equals(S.REGISTER_NO_APPROVAL_REQUIRED + "_body"))
      return t("!" + T.NAME + ",\n\nSu cuenta en !" + T.SITE + " ha sido creada.\n\nYa puede iniciar sesien !" + T.LOGIN_URI + " en el futuro usando:\n\nCodigo de usuario: !" + T.USERNAME, variables, langcode) + "\n\nContraseingresada por usted al crear su cuenta"; 
    if (key.equals(S.REGISTER_ADMIN_CREATED + "_subject"))
      return t("Un administrador creuna cuenta para usted en !" + T.SITE + ".", variables, langcode); 
    if (key.equals(S.REGISTER_ADMIN_CREATED + "_body"))
      return t("!" + T.NAME + ",\n\nUn administrador del sitio !" + T.SITE + " ha creado una cuenta para usted. Ahora puede iniciar una sesien !" + T.LOGIN_URI + " utilizando el siguiente nombre de usuario y la siguiente contrase\n\nNombre del usuario: !" + T.USERNAME + "\nContrase!" + T.PASSWORD + "\n\nTambipuede ingresar haciendo clic en este enlace o copiy pegen el navegador:\n!" + T.LOGIN_URL + "\n\nEsta URL ses vpara ingresar una vez, y sse puede usar en una ocasi\n\nDespude iniciar sesiserredirigido a !" + T.EDIT_URI + ", donde podrcambiar su contrase\n\n\n\n. El equipo de !" + T.SITE, variables, langcode); 
    if (key.equals(S.REGISTER_PENDING_APPROVAL + "_subject") || key.equals(S.REGISTER_PENDING_APPROVAL_ADMIN + "_body"))
      return t("Account details for !" + T.NAME + " at !" + T.SITE + " (pending admin approval)", variables, langcode); 
    if (key.equals(S.REGISTER_PENDING_APPROVAL + "_body"))
      return t("!" + T.NAME + ",\n\nThank you for registering at !" + T.SITE + ". Your application for an account is currently pending approval. Once it has been approved, you will receive another e-mail containing information about how to log in, set your password, and other details.\n\n\n--  !" + T.SITE + " team", variables, langcode); 
    if (key.equals(S.REGISTER_PENDING_APPROVAL_ADMIN + "_body"))
      return t("!" + T.NAME + " has applied for an account.\n\n!" + T.EDIT_URI, variables, langcode); 
    if (key.equals(S.PASSWORD_RESET + "_subject"))
      return t("Replacement login information for !" + T.NAME + " at !" + T.SITE + "", variables, langcode); 
    if (key.equals(S.PASSWORD_RESET + "_body"))
      return t("Hi !" + T.NAME + "!,\n\nA request to reset the password for your account has been made at !" + T.SITE + ".\n\nYou may now log in to !" + T.URI_BRIEF + " by clicking on this link or copying and pasting it in your browser:\n\n<a style=\"line-break: anywhere;\" href=\"!" + T.LOGIN_URL + "\">!" + T.LOGIN_URL + "</a>\n\nThis is a one-time login, so it can be used only once. It expires after one day and nothing will happen if it's not used.\n\nAfter logging in, you will be redirected to !" + T.EDIT_URI + " so you can change your password.", variables, langcode); 
    if (key.equals(S.STATUS_ACTIVATED + "_subject"))
      return t("Account details for !" + T.NAME + " at !" + T.SITE + " (approved)", variables, langcode); 
    if (key.equals(S.STATUS_ACTIVATED + "_body"))
      return t("!" + T.NAME + ",\n\nYour account at !" + T.SITE + " has been activated.\n\nYou may now log in by clicking on this link or copying and pasting it in your browser:\n\n!" + T.LOGIN_URL + "\n\nThis is a one-time login, so it can be used only once.\n\nAfter logging in, you will be redirected to !" + T.EDIT_URI + " so you can change your password.\n\nOnce you have set your own password, you will be able to log in to !" + T.LOGIN_URI + " in the future using:\n\nusername: !username\n", variables, langcode); 
    if (key.equals(S.STATUS_BLOCKED + "_subject"))
      return t("Account details for !" + T.NAME + " at !" + T.SITE + " (blocked)", variables, langcode); 
    if (key.equals(S.STATUS_BLOCKED + "_body"))
      return t("!" + T.NAME + ",\n\nYour account on !site has been blocked.", variables, langcode); 
    if (key.equals(S.STATUS_DELETED + "_subject"))
      return t("Account details for !" + T.NAME + " at !" + T.SITE + " (deleted)", variables, langcode); 
    if (key.equals(S.STATUS_DELETED + "_body"))
      return t("!" + T.NAME + ",\n\nYour account on !" + T.SITE + " has been deleted.", variables, langcode); 
    return null;
  }
  
  public Map mailTokens(Map<Class<Enum>, T[]> tokens, User account, String language, Map values) {
    if (values == null) {
      tokens.put(Enum.class, T.values());
      return tokens;
    } 
    String base_url = X.url("", true);
    if (base_url.endsWith("/"))
      base_url = base_url.substring(0, base_url.length() - 1); 
    base_url = "http://web.regionancash.gob.pe";
    account = (User)values.get("account");
    String name = (String)values.get(T.NAME);
    String passResetUrl = passResetUrl(account);
    tokens.put(T.USERNAME, account.getName());
    tokens.put(T.NAME, (name != null) ? name : account.getName());
    tokens.put(T.SITE, this.systemFacade.getV("site_name", "_"));
    tokens.put(T.LOGIN_URL, base_url + passResetUrl);
    tokens.put(T.PASS_RESET_URL, base_url + passResetUrl);
    tokens.put(T.URI_BRIEF, base_url.replace("http://", ""));
    tokens.put(T.URI, base_url);
    tokens.put(T.MAILTO, account.getMail());
    tokens.put(T.DATE, XDate.toString(new Date(), "dd/MMM/yyyy"));
    tokens.put(T.LOGIN_URI, base_url + "/admin");
    tokens.put(T.EDIT_URI, base_url + "/admin/me");
    tokens.put(T.PASSWORD, (T[])values.get("pass"));
    return tokens;
  }
  
  public List getTemplateList() {
    ArrayList<String> templateList = new ArrayList<>();
    for (S o : S.values())
      templateList.add(o.toString()); 
    return templateList;
  }
  
  public void load() {
    X.getRequest().setAttribute(X.TEMPLATE, "/simpleTemplate.xhtml");
  }
  
  public User load(Object id) {
    EntityManager em = getEntityManager();
    User u = (User)find(Integer.valueOf(XUtil.intValue(id)));
    HashMap<Object, Object> ext = new HashMap<>();
    u.setExt(ext);
    u.setDruRoleCollection(getRoles(u));
    if (XUtil.intValue(u.getIdDir()) != 0) {
      People people = (People)em.find(People.class, u.getIdDir());
      em.detach(people);
      people.setDocument(null);
      ext.put("people", people);
    } 
    em.detach(u);
    u.setPass(null);
    ext.put("oldStatus", Short.valueOf(u.getStatus()));
    return u;
  }
  
  public void edit(User account) {
    Map<String, Object> ext = (Map)account.getExt();
    boolean admin = access(UserFacadeLocal.Perm.ADMIN_USERS);
    System.out.println("admin=" + admin);
    Object om = this.systemFacade.getV(S.USER_EMAIL_VERIFICATION.toString(), Boolean.valueOf(true));
    boolean USER_EMAIL_VERIFICATION = XUtil.booleanValue(om);
    boolean notify = XUtil.booleanValue(this.systemFacade.getV("NOTIFY", Boolean.valueOf(true)));
    int op = 1;
    if (!(ext.get("people") instanceof People) && ext.get("people") != null)
      ext.put("people", BeanUtils.getObject(People.class, ext.get("people"))); 
    String pass = (String)ext.get("clave");
    if (!XUtil.isEmpty(pass)) {
      if (!pass.equals(ext.get("confirm")))
        throw new RuntimeException("Contrasey Confirmacino son identicos"); 
      account.setPass((new Encrypter()).encode(Encrypter.MD5, pass));
    } 
    if (XUtil.isEmpty(account.getPass())) {
      System.out.println(" geerar pass " + account);
      account.setPass((new Encrypter()).encode(Encrypter.MD5, pass = RandomUtil.getW(10, false)));
    } 
    ext.put("pass", pass);
    EntityManager em = getEntityManager();
    People people = (People)ext.get("people");
    if (people != null)
      account.setIdDir(people.getId()); 
    if (account.getUid() == null) {
      if (account.getUid() != null && account.getUid().intValue() == 1) {
        admin = true;
        notify = false;
      } 
      if (!admin && USER_EMAIL_VERIFICATION)
        account.setStatus((short)1); 
      if (account.getCreated() == 0)
        account.setCreated((int)(X.getServerDate().getTime() / 1000L)); 
      if (existsNameXorMail(account.getName(), account.getMail()))
        throw new RuntimeException("El Nombre o Correo Electronico ya esta registrado en el sistema"); 
      create(account);
    } else {
      super.edit(account);
      op = 0;
    } 
    if (ext.containsKey("people")) {
      people = (People)ext.get("people");
      if (people != null) {
        User oo = (User)this.sessionFacade.get("_USER");
        boolean bb = false;
        if (oo == null || XUtil.intValue(oo.getUid()) == 0) {
          this.sessionFacade.put("_USER", account);
          bb = true;
        } 
        people = (People)em.find(People.class, people.getId());
        try {
          ((UserFacadeLocal.CrudModule<User>)getModule(UserFacadeLocal.CrudModule.class, people
              .getClass().getSimpleName())).afterEdit(account, people);
        } catch (Exception e) {
          System.out.println("UserFacade.edit=" + e);
        } 
        if (bb)
          this.sessionFacade.put("people", people); 
      } 
    } 
    if (account.getUid().intValue() != 1 && account.getRoleCollection() != null) {
      em.createQuery("DELETE FROM UserRole u WHERE u.PK.uid=:uid").setParameter("uid", account.getUid()).executeUpdate();
      account.getRoleCollection().stream().forEach(r -> {
            UserRolPK pk = new UserRolPK((account.getUid().intValue() < 0) ? -account.getIdDir().intValue() : account.getUid().intValue(), r.getRid().intValue());
            em.merge(new UserRole(pk));
          });
    } 
    notify = false;
    if (admin && !notify) {
      switch (op) {
        case 1:
          ext.put("_MSG", "Se creuna nueva cuenta de usuario para <a href='" + X.url("user/" + account.getUid()) + "'>" + account.getName() + "</a>. No se ha enviado mensaje a correo.");
          ext.put("_DESTINY", "user/" + account.getUid() + "/edit");
          return;
        case 0:
          ext.put("_MSG", "Los cambios en la cuenta han sido guardados.");
          ext.put("_DESTINY", "user/" + account.getUid());
          break;
      } 
    } else if (!USER_EMAIL_VERIFICATION && account.getStatus() > 0 && !admin) {
      mailNotify(ext, S.REGISTER_NO_APPROVAL_REQUIRED.toString(), account, "");
      login(account.getName(), pass, new HashMap<>());
      ext.put("_MSG", "Registration successful. You are now logged in.");
    } else if (account.getStatus() > 0 || notify) {
      String opp = (notify ? S.REGISTER_ADMIN_CREATED : S.REGISTER_NO_APPROVAL_REQUIRED).toString();
      if (notify) {
        X.log("op=" + op);
        if (op == 0) {
          short oldStatus = (short)XUtil.intValue(ext.get("oldStatus"));
          X.log("op=" + op + ";x=" + ((oldStatus != account.getStatus()) ? 1 : 0));
          if (oldStatus != account.getStatus())
            mailNotify(ext, ((account.getStatus() == 1) ? S.STATUS_ACTIVATED : S.STATUS_BLOCKED).toString(), account, "es"); 
          ext.put("_MSG", "Los cambios en la cuenta han sido guardados.");
        } else {
          mailNotify(ext, opp, account, "es");
          ext.put("_MSG", "Contrasey otras instrucciones se han enviado por correo electral nuevo usuario <a href='" + X.url("user/" + account.getUid()) + "'>" + account.getName() + "</a>.");
          ext.put("_DESTINY", "admin");
        } 
      } else {
        mailNotify(ext, opp, account, "es");
        ext.put("_MSG", "Su contrasey otras instrucciones han sido enviado a su direccide correo electr");
      } 
    } else {
      mailNotify(ext, S.REGISTER_PENDING_APPROVAL.toString(), account, "es");
      ext.put("_MSG", "Gracias por solicitar una cuenta. Su cuenta estpendiente de aprobacipor parte del administrador del sitio . <br/> Mientras tanto, un mensaje de bienvenida con instrucciones adicionales ha sido enviada a su direccide correo electr");
    } 
  }
  
  public Map mail(Map m, String key, Map<String, String> message) {
    String language = (String)message.get("language");
    Map variables = mailTokens(new HashMap<>(), (User)m.get("user"), language, m);
    message.put("subject", (new StringBuilder()).append(message.get("subject")).append(mailText(key + "_subject", language, variables)).toString());
    ((XMap)message.get("body")).add(mailText(key + "_body", language, variables));
    return message;
  }
  
  public String passResetUrl(User account) {
    long passResetTimestamp = X.getServerDate().getTime() / 1000L;
    return X.url("user/reset/" + account.getUid() + "/" + passResetTimestamp + "/" + passRehash(account.getPass(), passResetTimestamp, account.getLogin()));
  }
  
  private String passRehash(String pass, long timestamp, long login) {
    return (new Encrypter()).encode(Encrypter.MD5, pass + timestamp + login);
  }
  
  private boolean mailNotify(Map<T, String> m, String op, User account, String language) {
    boolean default_notify = (!op.equals(S.STATUS_DELETED.toString()) && !op.equals(S.STATUS_BLOCKED.toString()));
    boolean notify = XUtil.booleanValue(this.systemFacade.getV("USER_MAIL_" + op + "_notify", Boolean.valueOf(default_notify)));
    if (notify) {
      XMap params = new XMap(new Object[] { "account", account });
      if (XUtil.intValue(account.getIdDir()) != 0) {
        try {
          m.put(T.NAME, getEntityManager().createQuery("SELECT p.names FROM People p WHERE p.id=:peopleId").setParameter("peopleId", account.getIdDir()).getSingleResult().toString().split(" ")[0]);
        } catch (NoResultException noResultException) {}
      } else {
        People people = (People)m.get("people");
        if (people != null)
          m.put(T.NAME, ("" + people.getNames()).split(" ")[0]); 
      } 
      m.put("account", account);
      this.contactFacade.mail(m, this, op, account.getMail(), language, (Map)params);
      if (op.equals(S.REGISTER_PENDING_APPROVAL.toString()))
        this.contactFacade.mail(m, this, S.REGISTER_PENDING_APPROVAL_ADMIN
            
            .toString(), this.systemFacade
            .getV("site_mail", "").toString(), language, (Map)params); 
    } 
    return true;
  }
  
  public Object passReset(int uid, long requestPassTimestamp, String hashedPass, Map<String, String> m) {
    User u = (User)this.sessionFacade.get("_USER");
    if (u != null && u.getUid().intValue() != 0)
      throw new SimpleException("Ud. ha usado ya este enlace de inicio de sesiunico. No es necesario usarlo usar este link. Ud. ya ha iniciado la sesi, /"); 
    long timeout = 86400L;
    long current = (new Date()).getTime() / 1000L;
    EntityManager em = getEntityManager();
    User account = (User)em.find(User.class, Integer.valueOf(uid));
    if (requestPassTimestamp < current && account != null) {
      if (account.getLogin() > 0L && current - requestPassTimestamp > timeout)
        throw new SimpleException("You have tried to use a one-time login link that has expired. Please request a new one using the form below.", "/password"); 
      if (account.getUid().intValue() != 0 && requestPassTimestamp > account
        .getLogin() && current > requestPassTimestamp && hashedPass
        
        .equals(passRehash(account.getPass(), requestPassTimestamp, account.getLogin()))) {
        if ("update".equals(m.remove("ACTION"))) {
          u = account;
          String newPass = (String)m.get("clave");
          String confirmPass = (String)m.get("confirm");
          if (XUtil.isEmpty(newPass))
            throw new SimpleException("Contraseno puede ser en blanco"); 
          if (!newPass.equals(confirmPass))
            throw new SimpleException("Contrasenueva y su confirmacion deben ser iguales"); 
          u.setPass((new Encrypter()).encode(Encrypter.MD5, newPass));
          authenticateFinalize(u);
          return new SimpleException("Acabas de utilizar su enlace de inicio de sesiYa no sera posible utilizar nuevamente este enlace para iniciar sesiPor favor, cambie su contrase", "admin");
        } 
      } else {
        throw new SimpleException("Has tratado de utilizar un enlace de inicio de sesiwhich has either been used or is no longer valid. Please request a new one using the form below.", "/password");
      } 
    } 
    requestPassTimestamp = Long.parseLong(m.get("timestamp").toString());
    u = account;
    m.put("name", (u.getExt() != null) ? ((People)u.getExt()).getFullName() : u.getName());
    m.put("account", account);
    m.put("expirationDate", new Date((requestPassTimestamp + timeout) * 1000L));
    return null;
  }
  
  public int password(Map m) throws Exception {
    String name = (String)m.get("name");
    User user = null;
    try {
      user = (User)getEntityManager().createQuery("SELECT u FROM User u WHERE (LOWER(u.name)=:name OR LOWER(u.mail)=:name)", User.class).setParameter("name", name.toLowerCase()).getSingleResult();
    } catch (NoResultException n) {
      for (String mn : getModuleNameList()) {
        if (user != null)
          break; 
        try {
          user = ((UserFacadeLocal.UserModule)getModule(UserFacadeLocal.UserModule.class, mn)).password(m);
        } catch (SimpleException simpleException) {
        
        } catch (RuntimeException e) {
          X.log(e.getMessage());
        } 
      } 
    } 
    if (user != null && user.getStatus() > 0) {
      if (!mailNotify(m, S.PASSWORD_RESET.toString(), user, "es"))
        throw new SimpleException("Ha sucedido un error al enviar informacion por correo."); 
    } else {
      throw new SimpleException("El codigo de usuario o correo no se encuentra registrado o esta inactivo, comuniquese con el administrador.");
    } 
    return 0;
  }
  
  public static String t(String t, Map variables, String c) {
    return t;
  }
  
  public void send(Map<String, Object> m) {
    m.put("account", this.sessionFacade.get("_USER"));
    this.contactFacade.mail(m, this, S.REGISTER_NO_APPROVAL_REQUIRED + "_body", (String)m.get("destiny"), "", m);
  }
  
  private void authenticateFinalize(User user) {
    user.setLogin(X.getServerDate().getTime() / 1000L);
    EntityManager em = getEntityManager();
    if (user.getUid().intValue() > 0) {
      em.merge(user);
    } else if (user.getUid().intValue() < 0) {
      user.setPass((new Encrypter()).encode(Encrypter.MD5, RandomUtil.getW(10, false)));
      Long login = Long.valueOf(user.getLogin());
      user.setCreated((int)((login != null) ? login.intValue() : ((new Date()).getTime() / 100L)));
      user.setStatus((short)1);
      em.persist(user);
    } 
    Object destiny = this.sessionFacade.get("_DESTINY");
    this.sessionFacade.invalidate();
    if (user.getUid().intValue() > 0)
      user = (User)em.find(User.class, user.getUid()); 
    HashMap<Object, Object> ext = new HashMap<>();
    if (XUtil.intValue(user.getIdDir()) > 0) {
      People people = (People)em.find(People.class, user.getIdDir());
      System.out.println("finalizre " + people + " people=" + people.getExt() + " user.idDir=" + user.getIdDir());
      people.setExt(new HashMap<>());
      em.detach(user);
      user.setPass(null);
      em.detach(people);
      people.setDocument(null);
      People p = new People();
      p.setFullName(people.getFullName());
      p.setNames(people.getNames());
      p.setSex(people.getSex());
      p.setMail(people.getMail());
      p.setBirthdate(people.getBirthdate());
      p.setApPaterno(people.getApPaterno());
      p.setApMaterno(people.getApMaterno());
      p.setCode(people.getCode());
      p.setStatus(people.getStatus());
      p.setId(people.getId());
      ext.put(People.class.getName(), p);
      ext.put("people", p);
      this.sessionFacade.put("people", p);
      this.sessionFacade.put(People.class.getName(), p);
    } 
    user.setExt(ext);
    this.sessionFacade.put("_USER", user);
    this.sessionFacade.put("_DESTINY", destiny);
    if ("".equals(X.getRequest().getContextPath())) {
      Login login = new Login();
      login.setUid(user.getUid().intValue());
      login.setAccessDate(new Timestamp(X.getServerDate().getTime()));
      login.setPeopleId(XUtil.intValue(user.getIdDir()));
      if (login.getId() == null) {
        em.persist(login);
      } else {
        em.merge(login);
      } 
      this.sessionFacade.put("login", login);
      String token = X.toText(X.getClientIpAddr(X.getRequest())).replace(".", "") + "." + login.getId() + "." + X.getRequest().getSession().getId();
      this.sessionFacade.put("TOKEN", token);
    } 
    for (String mn : getModuleNameList()) {
      try {
        ((UserFacadeLocal.UserModule)getModule(UserFacadeLocal.UserModule.class, mn)).authenticateFinalize(user);
      } catch (RuntimeException e) {
        System.out.println(mn + ".authenticateFinalize>" + e.getLocalizedMessage());
      } 
    } 
  }
  
  private static boolean ALL_ACCESS = true;
  
  public User getByDni(String dni) {
    EntityManager em = getEntityManager();
    List<People> lp = em.createQuery("SELECT p FROM People p WHERE p.code LIKE :dni").setParameter("dni", dni).getResultList();
    ArrayList li = new ArrayList();
    lp.stream().forEach(pn -> li.add(pn.getId()));
    List<?> lu;
    if (lp.size() > 0 && (lu = em.createQuery("SELECT u FROM User u WHERE u.idDir IN (" + XUtil.implode(li, ",") + ")").getResultList()).size() > 0)
      return (User)lu.get(0); 
    return null;
  }
  
  public Collection<Role> getRoles(User user) {
    EntityManager em = getEntityManager();
    if (user.getUid().intValue() < 0) {
      user.setDruRoleCollection(em.createQuery("SELECT r FROM Role r,UserRole u WHERE r.rid=u.PK.rid AND u.PK.uid=:uid", Role.class).setParameter("uid", Integer.valueOf(-user.getIdDir().intValue())).getResultList());
    } else if (user.getUid().intValue() > 0) {
      user.setDruRoleCollection(em.createQuery("SELECT r FROM Role r,UserRole u WHERE r.rid=u.PK.rid AND u.PK.uid=:uid", Role.class).setParameter("uid", user.getUid()).getResultList());
    } else {
      return anonymous.getRoleCollection();
    } 
    ArrayList<Role> roles = (ArrayList<Role>)user.getRoleCollection();
    if (roles == null)
      roles = new ArrayList<>(); 
    if (!roles.contains(AUTHENTICATED))
      roles.add(AUTHENTICATED); 
    for (Role role : roles) {
      em.detach(role);
      role.setPermissionCollection(null);
    } 
    System.out.println("los roles de " + user + " son " + roles);
    return roles;
  }
  
  public boolean access(Object perm) {
    return access(perm, (User)this.sessionFacade.get("_USER"), false);
  }
  
  public boolean access(Object perm, User user, boolean reset) {
    if (user == null)
      return false; 
    List<Integer> perms = (List)this.sessionFacade.get("perms");
    if (reset)
      perms = null; 
    switch (user.getUid().intValue()) {
      case 1:
        return true;
      case 0:
        if (perms_anonymous == null) {
          List<Role> roles = (List<Role>)getRoles(user);
          perms = perms_anonymous = new ArrayList();
          for (Role role : roles)
            perms.add(role.getRid()); 
          List l = getEntityManager().createQuery("SELECT p.perm FROM Role r JOIN r.permissionCollection p WHERE r.rid IN (" + XUtil.toString(perms) + ")").getResultList();
          perms.clear();
          Map<String, List<Integer>> ext = (Map)user.getExt();
          if (ext != null)
            ext.put("perms", perms); 
          this.sessionFacade.put("perms", perms);
          for (Object o : l) {
            for (String s : o.toString().split(",")) {
              if (s.length() > 0)
                perms.add(s.trim()); 
            } 
          } 
        } 
        break;
    } 
    if (perms == null) {
      EntityManager em = getEntityManager();
      Map<String, List<Integer>> ext = (Map)user.getExt();
      List<Role> roles = (List<Role>)getRoles(user);
      perms = new ArrayList<>();
      for (Role role : roles) {
        em.detach(role);
        role.setPermissionCollection(null);
        perms.add(role.getRid());
      } 
      List l = em.createQuery("SELECT p.perm FROM Role r JOIN Permission p ON p.role=r WHERE r.rid IN (" + XUtil.toString(perms) + ")").getResultList();
      perms.clear();
      ext.put("perms", perms);
      this.sessionFacade.put("perms", perms);
      for (Object o : l) {
        for (String s : o.toString().split(",")) {
          if (s.length() > 0)
            perms.add(s.trim()); 
        } 
      } 
      for (String mn : getModuleNameList()) {
        try {
          ((UserFacadeLocal.UserModule)getModule(UserFacadeLocal.UserModule.class, mn)).loadPerm(user, perms);
        } catch (RuntimeException e) {
          X.log(e.getMessage());
        } 
      } 
    } 
    if (perm instanceof String) {
      for (String s : ((String)perm).split(",")) {
        if (perms.contains(s))
          return true; 
      } 
      return false;
    } 
    if (perm instanceof MenuRouter) {
      MenuRouter menuRouter = (MenuRouter)perm;
      String accessCallback = menuRouter.getAccessCallback();
      String accessArgument = menuRouter.getAccessArguments();
      if (accessArgument.length() > 0 && perms.contains(accessArgument))
        return true; 
      return perms.contains(menuRouter.getPath());
    } 
    return perms.contains("" + perm);
  }
  
  public void logout() {
    User user = getCurrentUser();
    for (String mn : getModuleNameList()) {
      try {
        UserFacadeLocal.UserModule um = (UserFacadeLocal.UserModule)getModule(UserFacadeLocal.UserModule.class, mn);
        user = um.logout(user);
        X.log("Logout session usando for user=" + user);
      } catch (RuntimeException|AbstractMethodError e) {
        System.out.println("userFacade.logout->" + e);
      } 
    } 
    this.sessionFacade.logout();
  }
  
  public User login(String name, String pass, Map m) {
    EntityManager em = getEntityManager();
    User user = null;
    try {
      name = name.trim().toLowerCase();
      user = (User)em.createQuery("SELECT u FROM User u WHERE (LOWER(u.name)=:name OR LOWER(u.mail)=:name) AND u.pass=:pass").setParameter("name", name).setParameter("pass", (new Encrypter()).encode(Encrypter.MD5, pass)).getSingleResult();
      if (user.getStatus() == 0)
        return null; 
    } catch (NoResultException noResultException) {
      X.log("Failed attemp for " + name + " using " + pass + "=" + (new Encrypter()).encode(Encrypter.MD5, pass));
    } catch (Exception e) {
      throw new RuntimeException(e);
    } 
    X.log("userFacade.user=" + user);
    for (String mn : getModuleNameList()) {
      if (user != null)
        break; 
      try {
        user = ((UserFacadeLocal.UserModule)getModule(UserFacadeLocal.UserModule.class, mn)).login(name, pass, m);
        X.log("Iniciando session usando " + mn + " resulta user=" + user);
      } catch (RuntimeException e) {
        X.log(e);
      } 
    } 
    if (user != null)
      authenticateFinalize(user); 
    return user;
  }
  
  public User initSession(Integer uid) {
    User user = (User)find(uid);
    if (user != null)
      authenticateFinalize(user); 
    return user;
  }
  
  public User getUserByDir(int idDir) {
    try {
      List<User> l = getEntityManager().createQuery("SELECT u FROM User u WHERE u.idDir=:idDir").setParameter("idDir", Integer.valueOf(idDir)).getResultList();
      if (!l.isEmpty())
        return l.get(0); 
    } catch (Exception e) {
      System.out.println("userFacade.getUserByDir->" + e);
    } 
    return null;
  }
  
  public boolean confirm(String pass) {
    User user = (User)this.sessionFacade.get("_USER");
    user = (User)getEntityManager().find(User.class, user.getUid());
    return user.getPass().equals((new Encrypter()).encode(Encrypter.MD5, pass));
  }
  
  public void changePassword(String currentPass, String newPass, String confirmPass) {
    if (XUtil.isEmpty(newPass))
      throw new SimpleException("Contraseno puede ser en blanco"); 
    if (!newPass.equals(confirmPass))
      throw new SimpleException("Contrasenueva y su confirmacion deben ser iguales"); 
    if (!confirm(currentPass))
      throw new SimpleException("Contraseactual ingresada no es la correcta"); 
    User user = (User)this.sessionFacade.get("_USER");
    EntityManager em = getEntityManager();
    User u = (User)em.find(User.class, user.getUid());
    u.setPass((new Encrypter()).encode(Encrypter.MD5, newPass));
    em.merge(u);
  }
  
  @PostConstruct
  public void init() {
    add(this);
    add(this);
  }
  
  public Object getBlock(HttpServletRequest request, String op, Object delta) {
    if ("list".equals(op)) {
      Map<String, XMap> blocks = (Map)request.getAttribute("#blocks");
      blocks.put("0", new XMap(new Object[] { "info", "User login" }));
      blocks.put("1", new XMap(new Object[] { "info", "Navigation" }));
      blocks.put("2", new XMap(new Object[] { "info", "Who's new" }));
      blocks.put("3", new XMap(new Object[] { "info", "Who's online" }));
      blocks.put("4", new XMap(new Object[] { "info", "Herramienta desarrollo" }));
      return blocks;
    } 
    if ("view".equals(op)) {
      String[] q;
      User user = (User)this.sessionFacade.get("_USER");
      switch (XUtil.intValue(delta)) {
        case 0:
          q = (String[])request.getAttribute("#q");
          if ((user != null && user.getUid().intValue() != 0) || (q.length > 0 && "user".equalsIgnoreCase(q[0])))
            break; 
          return new XMap(new Object[] { "src", "/user/login.xhtml" });
        case 4:
          return new XMap(new Object[] { "title", "Memoria", "content", SystemUtilities.getMemory() });
      } 
    } 
    return null;
  }
  
  static {
    connectedUser = new ArrayList();
    AUTHENTICATED = new Role(Integer.valueOf(2), "");
  }
  
  public boolean existsNameXorMail(String name, String mail) {
    if (mail != null) {
      if (name == null)
        return (XUtil.intValue(getEntityManager().createQuery("SELECT COUNT(u) FROM User u WHERE u.mail=:mail")
            .setParameter("mail", mail).getSingleResult()) > 0); 
      return (XUtil.intValue(getEntityManager().createQuery("SELECT COUNT(u) FROM User u WHERE u.mail=:mail OR u.name=:name")
          .setParameter("mail", mail).setParameter("name", name).getSingleResult()) > 0);
    } 
    return (XUtil.intValue(getEntityManager().createQuery("SELECT COUNT(u) FROM User u WHERE u.name=:name")
        .setParameter("name", name).getSingleResult()) > 0);
  }
  
  public User findByName(String name) {
    List<?> lu;
    if ((lu = getEntityManager().createQuery("SELECT u FROM User u WHERE UPPER(u.name)=:name").setParameter("name", name.toUpperCase()).getResultList()).size() > 0)
      return (User)lu.get(0); 
    return null;
  }
  
  public People getDrtPersonaNatural(int id) {
    return (People)getEntityManager().find(People.class, Integer.valueOf(id));
  }
  
  public User getCurrentUser() {
    return (User)this.sessionFacade.get("_USER");
  }
  
  public User initSessionByToken(String access_token) {
    int loginId = XUtil.intValue(access_token.split(".")[0]);
    Login login = (Login)getEntityManager().find(Login.class, Integer.valueOf(loginId));
    return initSession(Integer.valueOf(login.getUid()));
  }
  
  enum T {
    DATE, PASSWORD, URI_BRIEF, URI, LOGIN_URI, LOGIN_URL, PASS_RESET_URL, SITE, EDIT_URI, USERNAME, NAME, COMPLETE_NAME, MAILTO;
  }
  
  enum S {
    PREFIX, USER_EMAIL_VERIFICATION, REGISTER_ADMIN_CREATED, REGISTER_NO_APPROVAL_REQUIRED, REGISTER_PENDING_APPROVAL, REGISTER_PENDING_APPROVAL_ADMIN, STATUS_DELETED, STATUS_ACTIVATED, STATUS_BLOCKED, PASSWORD_RESET, USER_REGISTRATION_HELP;
  }
  
  class UserListener implements HttpSessionBindingListener {
    User user;
    
    public UserListener(User u) {
      this.user = u;
    }
    
    public String toString() {
      return this.user.toString();
    }
    
    public void valueBound(HttpSessionBindingEvent hsbe) {
      UserFacade.connectedUser.add(this);
    }
    
    public void valueUnbound(HttpSessionBindingEvent hsbe) {
      UserFacade.connectedUser.remove(this);
    }
  }
  
  public static void main(String[] args) {
    System.out.println("");
    long m = (new Date()).getTime() / 1000L;
    System.out.println("m=" + m);
    int in = (int)((new Date()).getTime() / 1000L);
    System.out.println("in=" + in);
    Number n = Integer.valueOf(in);
    System.out.println("fecha=" + (new SimpleDateFormat("dd/MM/yyyy")).format(new Date(n.longValue() * 1000L)));
    System.out.println("fecha=" + (new SimpleDateFormat("dd/MM/yyyy")).format((new SystemController()).toDate(n)));
  }
}
