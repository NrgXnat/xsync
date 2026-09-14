package org.nrg.xsync.remote.alias;

import java.io.Serial;
import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;
import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;

/**
 * @author Mohana Ramaratnam
 *
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"remote_host", "local_project"}))
public class RemoteAliasEntity extends AbstractHibernateEntity {
	
	private static final long serialVersionUID = 1022020998456370664L;

	public void setRemote_alias_token(String remote_alias_token) {
		this.remote_alias_token = remote_alias_token;
		this.acquiredTime = new Date();
	}

    private String remote_alias_token;
    @Setter
    private String remote_alias_password;
    @Setter
    private String remote_host;
    @Setter
    private String local_project;
    @Setter
    Date acquiredTime;
    @Setter
    Date estimatedExpirationTime;
}
