package jrds.webapp;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jrds.Configuration;
import jrds.HostsList;
import jrds.Util;
import jrds.starter.Timer;

import org.apache.log4j.Logger;

/**
 * Servlet implementation class Cmd
 */
@ServletSecurity
public class Cmd extends JrdsServlet {
    static final private Logger logger = Logger.getLogger(Cmd.class);

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        ParamsBean params = new ParamsBean(req, getHostsList(), "command", "arg");

        String command = params.getValue("command");
        if(command == null || "".equals(command)) {
            command = req.getServletPath().substring(1);
        }
        logger.debug(Util.delayedFormatString("Command found: %s", command));

        if(! allowed(params, getPropertiesManager().adminACL, req, res))
            return;

        if("reload".equalsIgnoreCase(command)) {
            ServletContext ctxt = getServletContext();
            //only one reload allowed to run, just ignore synchronous reload
            if ( ReloadHostList.reloading.tryAcquire()) {
                reload(ctxt);
            }
            res.sendRedirect(req.getContextPath() + "/");
        }
        else if("pause".equalsIgnoreCase(command)) {
            ServletContext ctxt = getServletContext();
            pause(ctxt, params.getValue("arg"));
            res.sendRedirect(req.getContextPath() + "/");
        }
    }

    private void reload(final ServletContext ctxt) {
        Thread configthread = new Thread("jrds-new-config") {
            @Override
            public void run() {
                StartListener sl = (StartListener) ctxt.getAttribute(StartListener.class.getName());
                Properties p = sl.readProperties(ctxt);
                Configuration.switchConf(p);
                ReloadHostList.reloading.release();
                logger.info("Configuration rescaned");
            }
        };
        configthread.start();
    }

    private void pause(final ServletContext ctxt, final String arg) {       
        Thread configthread = new Thread("jrds-pause") {
            @Override
            public void run() {
                HostsList hl = Configuration.get().getHostsList();
                try {
                    for(Timer t: hl.getTimers()) {
                        t.lockCollect();
                    }
                    Thread.sleep(jrds.Util.parseStringNumber(arg, 1) * 1000 );
                } catch (InterruptedException e) {
                }
                for(Timer t: hl.getTimers()) {
                    t.releaseCollect();
                }
                logger.info("collect restarted");
            }
        };
        configthread.start();
    }

}
