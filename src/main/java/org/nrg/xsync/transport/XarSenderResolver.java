package org.nrg.xsync.transport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Selects the {@link XarSender} to use for a project.
 *
 * <p>Non-default senders are tried in priority order; {@link HttpsXarSender}
 * is the default when none applies. Adding a new transport (e.g. Globus) means
 * adding its {@link XarSender} to the priority list here&mdash;the call sites in
 * the sync pipeline do not change.</p>
 *
 * @author XSync
 */
@Component
public class XarSenderResolver {

    private final AsperaXarSender _aspera;
    private final HttpsXarSender  _https;

    @Autowired
    public XarSenderResolver(final AsperaXarSender aspera, final HttpsXarSender https) {
        _aspera = aspera;
        _https = https;
    }

    /**
     * Resolve the sender for a project: the first configured non-default
     * sender, else HTTPS.
     *
     * @param projectId the local (source) project id
     * @return the sender to use
     */
    public XarSender resolve(final String projectId) {
        if (_aspera.supports(projectId)) {
            return _aspera;
        }
        return _https;
    }
}
