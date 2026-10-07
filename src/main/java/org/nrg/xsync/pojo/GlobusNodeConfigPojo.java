package org.nrg.xsync.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API carrier for this node's Globus configuration: the service account and the
 * outbox.
 *
 * <p>The {@code clientSecret} is <strong>write-only</strong>: accepted on input
 * but never serialized in responses. A blank secret on save leaves the stored
 * secret unchanged.</p>
 *
 * @author XSync
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobusNodeConfigPojo {

    private String clientId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String clientSecret;

    private String outboxCollectionId;
    private String outboxDirectory;
    private String inboxCollectionId;
}
