/*
  Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.jdbc.ha;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.mysql.cj.core.util.StringUtils;
import com.mysql.cj.jdbc.ConnectionImpl;

public class ServerAffinityStrategy extends RandomBalanceStrategy {
    public String[] affinityOrderedServers = null;

    public ServerAffinityStrategy(String affinityOrdervers) {
        super();
        if (!StringUtils.isNullOrEmpty(affinityOrdervers)) {
            this.affinityOrderedServers = affinityOrdervers.split(",");
        }
    }

    @Override
    public ConnectionImpl pickConnection(LoadBalancedConnectionProxy proxy, List<String> configuredHosts, Map<String, ConnectionImpl> liveConnections,
            long[] responseTimes, int numRetries) throws SQLException {
        if (this.affinityOrderedServers == null) {
            return super.pickConnection(proxy, configuredHosts, liveConnections, responseTimes, numRetries);
        }
        Map<String, Long> blackList = proxy.getGlobalBlacklist();

        for (String host : this.affinityOrderedServers) {
            if (configuredHosts.contains(host) && !blackList.containsKey(host)) {
                ConnectionImpl conn = liveConnections.get(host);
                if (conn != null) {
                    return conn;
                }
                try {
                    conn = proxy.createConnectionForHost(host);
                    return conn;
                } catch (SQLException sqlEx) {
                    if (proxy.shouldExceptionTriggerConnectionSwitch(sqlEx)) {
                        proxy.addToGlobalBlacklist(host);
                    }
                }
            }
        }

        // Failed to connect to all hosts in the affinity list. Delegate to RandomBalanceStrategy.
        return super.pickConnection(proxy, configuredHosts, liveConnections, responseTimes, numRetries);
    }
}