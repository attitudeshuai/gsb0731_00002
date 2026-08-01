package com.dbtool.backend.service;

import com.dbtool.backend.dto.ConnectionRequest;
import com.dbtool.backend.entity.Connection;
import com.dbtool.backend.repository.ConnectionRepository;
import com.dbtool.backend.security.AesCryptoService;
import com.dbtool.backend.target.TargetDataSourceManager;
import com.dbtool.backend.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConnectionService {

    private final ConnectionRepository repository;
    private final AesCryptoService crypto;
    private final TargetDataSourceManager dataSourceManager;

    public ConnectionService(ConnectionRepository repository,
                             AesCryptoService crypto,
                             TargetDataSourceManager dataSourceManager) {
        this.repository = repository;
        this.crypto = crypto;
        this.dataSourceManager = dataSourceManager;
    }

    public List<Connection> findAll() {
        return repository.findAll();
    }

    public Connection findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Connection not found: " + id));
    }

    @Transactional
    public Connection create(ConnectionRequest req) {
        Connection c = new Connection();
        apply(c, req, true);
        return repository.save(c);
    }

    @Transactional
    public Connection update(Long id, ConnectionRequest req) {
        Connection c = findById(id);
        apply(c, req, false);
        Connection saved = repository.save(c);
        // config changed -> drop cached pool
        dataSourceManager.evict(id);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Connection not found: " + id);
        }
        repository.deleteById(id);
        dataSourceManager.evict(id);
    }

    /** Test a saved connection using its stored (decrypted) credentials. */
    public void testExisting(Long id) {
        Connection c = findById(id);
        dataSourceManager.testConnection(c.getHost(), c.getPort(), c.getUsername(),
                crypto.decrypt(c.getPasswordEncrypted()), c.getDatabaseName());
    }

    /** Test an ad-hoc connection with plaintext credentials from the request. */
    public void testAdHoc(ConnectionRequest req) {
        dataSourceManager.testConnection(req.host, req.port, req.username, req.password, req.databaseName);
    }

    private void apply(Connection c, ConnectionRequest req, boolean isCreate) {
        c.setName(req.name);
        c.setGroupId(req.groupId);
        c.setHost(req.host);
        c.setPort(req.port);
        c.setUsername(req.username);
        c.setDatabaseName(req.databaseName);
        c.setDbType(req.dbType == null ? "mysql" : req.dbType);

        if (req.password != null && !req.password.isEmpty()) {
            // encrypt with AES before persisting
            c.setPasswordEncrypted(crypto.encrypt(req.password));
        } else if (isCreate) {
            // allow empty password (some local dbs) -> store encrypted empty string
            c.setPasswordEncrypted(crypto.encrypt(""));
        }
        // on update with null/empty password: keep existing encrypted value
    }
}
