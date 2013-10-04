/*
 *  mod_cluster
 *
 *  Copyright(c) 2008 Red Hat Middleware, LLC,
 *  and individual contributors as indicated by the @authors tag.
 *  See the copyright.txt in the distribution for a
 *  full listing of individual contributors.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library in the file COPYING.LIB;
 *  if not, write to the Free Software Foundation, Inc.,
 *  59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 *
 * @author Jean-Frederic Clere
 * @version $Revision$
 */

package org.jboss.mod_cluster;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.catalina.Engine;
import org.apache.catalina.Service;
import org.jboss.modcluster.ModClusterService;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardServer;

public class TestStickyRemove extends TestCase {

    /* Test failover */
    public void testStickyRemove() {

        boolean clienterror = false;
        StandardServer server = new StandardServer();
        JBossWeb service = null;
        JBossWeb service2 = null;
        Connector connector = null;
        Connector connector2 = null;
        ModClusterService cluster = null;
        System.out.println("TestStickyRemove Started");

        System.setProperty("org.apache.catalina.core.StandardService.DELAY_CONNECTOR_STARTUP", "false");
        try {
            // server = (StandardServer) ServerFactory.getServer();

            service = new JBossWeb("node3",  "localhost");
            connector = service.addConnector(8010);
            server.addService(service);

            service2 = new JBossWeb("node4",  "localhost");
            connector2 = service2.addConnector(8889);
            server.addService(service2);

            cluster = Maintest.createClusterListener(server, "224.0.1.105", 23364, false, null, true, true, false, "secret");

        } catch(Exception ex) {
            ex.printStackTrace();
            fail("can't start service");
        }

        // start the server thread.
        ServerThread wait = new ServerThread(3000, server);
        wait.start();

        // Wait until httpd as received the nodes information.
        String [] nodes = new String[2];
        nodes[0] = "node3";
        nodes[1] = "node4";
        if (!Maintest.TestForNodes(cluster, nodes))
            fail("can't start nodes");

        // Start the client and wait for it.
        Client client = new Client();

        // Wait for it.
        try {
            if (client.runit("/MyCount", 10, false, true) != 0)
                clienterror = true;
        } catch (Exception ex) {
            ex.printStackTrace();
            clienterror = true;
        }

        // Stop the connector that has received the request...
        String node = client.getnode();
        if ("node4".equals(node)) {
            connector = connector2;
            node = "node3";
        } else {
            node = "node4";
        }
        if (connector != null) {
            try {
                connector.stop();
            } catch (Exception ex) {
                ex.printStackTrace();
                fail("can't stop connector");
            }
        }

        if (clienterror)
            fail("Client error");

        // Run a test on it. (it waits until httpd as received the nodes information).
        client.setnode(node);
        try {
            client.setdelay(30000);
            client.start();
            client.join();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (client.getresultok())
            System.out.println("Test DONE");
        else {
            System.out.println("Test FAILED");
            clienterror = true;
        }

        // Stop the server or services.
        try {
            wait.stopit();
            wait.join();
            server.removeService(service);
            server.removeService(service2);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        // Wait until httpd as received the stop messages.
        Maintest.testPort(8010);
        Maintest.testPort(8889);
        if (!Maintest.TestForNodes(cluster, null))
            System.out.println("Can't stop nodes");
        Maintest.StopClusterListener();

        // Test client result.
        if (clienterror)
            fail("Client test failed");
        if (client.requestedSessionId != null)
            fail("Client client received a sessionid");
        System.out.println("TestStickyRemove Done");
    }
}
