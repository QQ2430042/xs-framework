package com.xsframework.starter.data;

import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

public class SameDbConnectionProvider<T> extends AbstractMultiTenantConnectionProvider<T> {

    private final ConnectionProvider connectionProvider;

    public SameDbConnectionProvider(DataSource dataSource) {
        this.connectionProvider = new ConnectionProvider() {
            @Override
            public Connection getConnection() throws SQLException {
                return dataSource.getConnection();
            }

            @Override
            public void closeConnection(Connection connection) throws SQLException {
                connection.close();
            }

            @Override
            public boolean supportsAggressiveRelease() {
                return false;
            }

            @Override
            public boolean isUnwrappableAs(Class<?> unwrapType) {
                return DataSource.class.isAssignableFrom(unwrapType);
            }

            @Override
            public <X> X unwrap(Class<X> unwrapType) {
                return unwrapType.cast(dataSource);
            }
        };
    }

    @Override
    protected ConnectionProvider getAnyConnectionProvider() {
        return connectionProvider;
    }

    @Override
    protected ConnectionProvider selectConnectionProvider(T tenantIdentifier) {
        return connectionProvider;
    }
}
