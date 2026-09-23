package org.nrg.xsync.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API carrier for a configured Globus endpoint.
 *
 * <p>The {@code clientSecret} is <strong>write-only</strong>: it is accepted on
 * input (create/update) but never serialized in responses, so listing or
 * fetching endpoints does not expose stored secrets. When updating an existing
 * endpoint, a blank secret leaves the stored one unchanged.</p>
 *
 * @author XSync
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobusEndpointPojo {

    private String name;
    private String clientId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String clientSecret;

    private String inboxCollectionId;
    private String outboxCollectionId;
}
