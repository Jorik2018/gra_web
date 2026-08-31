package edu.uns.rest;

import java.util.ArrayList;
import java.util.Map;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.isobit.app.ejb.SessionFacadeLocal;
import org.isobit.app.ejb.UserFacadeLocal;
import org.isobit.app.jpa.User;
import org.isobit.app.ws.AbstractFacadeREST;
import org.isobit.directory.jpa.People;
import org.isobit.util.XMap;

@Stateless
@Path("login")
public class LoginFacadeREST extends AbstractFacadeREST<Object> {

    @EJB
    private UserFacadeLocal userFacade;

    @EJB
    private SessionFacadeLocal sessionFacade;

  @POST
  @Consumes({"application/json"})
  @Produces({"application/json;charset=UTF-8"})
  public Object login(Map m) {
     User user = this.userFacade.login((String)m.get("name"), (String)m.get("pass"), m);
     if (user != null) {
       Client client = ClientBuilder.newClient();
       Map map = (Map)client.target("http://127.0.0.1/api/auth")//http://200.48.170.148/api/auth
       .request().post(Entity.json(new XMap(new Object[] {
         "username", user.getName(), 
         "password", m.get("pass") 
        } )), Map.class);
       m.put("token", map.get("token"));
       m.remove("pass");
       m.put("user", user.getName());
       Object ext = user.getExt();
       if (ext instanceof Map) {
        Map me = (Map)ext;
         m.put("ext", new XMap(new Object[] { "perms", 
                 (me.get("perms") == null) ? new ArrayList() : me.get("perms") }));
         m.put("perms", (String)((Map)ext).get("perms"));
      } 
      m.put("uid", user.getUid());
       People pn = (People)this.sessionFacade.get("people");
       if (pn != null) {
         m.put("id", pn.getCode());
         m.put("people", new XMap(new Object[] { "id", pn.getId(), "fullName", pn.getFullName() }));
      } 
       this.sessionFacade.put("token", m.get("token"));
       return m;
    } 
     return Response.status(Response.Status.NOT_FOUND).build();
  }
}