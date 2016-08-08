package org.scada_lts.web.mvc.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.UserDao;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.web.integration.CrowdUtils;
import com.serotonin.mango.web.mvc.form.LoginForm;
import com.serotonin.util.StringUtils;
import com.serotonin.util.ValidationUtils;

@Controller
@RequestMapping("/login.htm") 
public class LoginController {    
    private static final Log LOG= LogFactory.getLog(LoginController.class);
    
	@RequestMapping(method = RequestMethod.GET)
	protected ModelAndView createForm(HttpServletRequest request) throws Exception {
		LOG.trace("/login.htm");
		
		return new ModelAndView("login");
	}
	
	@RequestMapping(method = RequestMethod.POST)
    protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, @ModelAttribute("login")LoginForm login) throws Exception {
		LOG.trace("/login.htm");
		boolean crowdAuthenticated = false;
        
        List<String> errors = new ArrayList<>();
        
        // Make sure there is a username
        if (StringUtils.isEmpty(login.getUsername()))
        	errors.add("login.validation.noUsername");

        // Make sure there is a password
        if (StringUtils.isEmpty(login.getPassword()))
        	errors.add("login.validation.noPassword");
        
        if (errors.isEmpty()){
	        User user = new UserDao().getUser(login.getUsername());
	        if (user == null)
	            errors.add("login.validation.noSuchUser");
	        else if (user.isDisabled())
	        	errors.add("login.validation.accountDisabled");
	        else {
	            if (CrowdUtils.isCrowdEnabled())
	                // First attempt authentication with Crowd.
	                crowdAuthenticated = CrowdUtils.authenticate(request, response, login.getUsername(),login.getPassword());
	
	            if (!crowdAuthenticated) {
	                String passwordHash = Common.encrypt(login.getPassword());
	
	                // Validating the password against the database.
	                if (!passwordHash.equals(user.getPassword()))
	                	errors.add("login.validation.invalidLogin");
	            }
	        }
        }

        ModelAndView mav;
        if (errors.isEmpty()) {
        	mav = performLogin(request, login.getUsername());
        } else {
        	mav = new ModelAndView("login");
        }
        mav.addObject("errors", errors);
        if (crowdAuthenticated)
            CrowdUtils.setCrowdAuthenticated(Common.getUser(request));
        return mav;
    }

    private ModelAndView performLogin(HttpServletRequest request, String username) {
        // Check if the user is already logged in.
        User user = Common.getUser(request);
        if (user != null && user.getUsername().equals(username)) {
            // The user is already logged in. Nothing to do.
            if (LOG.isDebugEnabled())
                LOG.debug("User is already logged in, not relogging in");
        }
        else {
            UserDao userDao = new UserDao();
            user = userDao.getUser(username);

            // Update the last login time.
            userDao.recordLogin(user.getId());

            // Add the user object to the session. This indicates to the rest
            // of the application whether the user is logged in or not.
            Common.setUser(request, user);
            if (LOG.isDebugEnabled())
                LOG.debug("User object added to session");
        }

        if (user.isFirstLogin())
            return new ModelAndView("help.shtm");
        else if (!StringUtils.isEmpty(user.getHomeUrl()))
            return new ModelAndView(new RedirectView(user.getHomeUrl()));
        else
        	return new ModelAndView(new RedirectView("watch_list.shtm"));
    }
}
