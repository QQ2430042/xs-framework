package com.xsframework.starter.data;

import com.xsframework.common.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

public class TenantRlsDataSource extends DelegatingDataSource {

    public TenantRlsDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        String tenantId = TenantContext.get();
        try (PreparedStatement statement = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
            statement.setString(1, tenantId == null || tenantId.isBlank() ? TenantIdResolver.SENTINEL : tenantId);
            statement.execute();
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
        return connection;
    }
}
