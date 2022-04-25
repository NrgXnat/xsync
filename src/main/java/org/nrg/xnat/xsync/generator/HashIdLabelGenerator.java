package org.nrg.xnat.xsync.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Slf4j
public class HashIdLabelGenerator implements XsyncLabelGeneratorI {
    /**
     * Generate new label for data based on date/time
     * @param user the user
     * @param item the item
     * @param projectSyncConfiguration the project sync configuration
     * @return the new label or null if not able to assign one
     */
    @Nullable
    @Override
    public String generateId(UserI user, XFTItem item, ProjectSyncConfiguration projectSyncConfiguration) {
        ItemI target = BaseElement.GetGeneratedItem(item);
        if (target instanceof XnatSubjectdata) {
            return hashId(((XnatSubjectdata) target).getId());
        } else if (target instanceof XnatExperimentdataI) {
            return hashId(((XnatExperimentdataI) target).getId());
        } else {
            log.warn("Unable to generate label for target because it is not a subject or experiment: {}", target);
            return null;
        }
    }

    @Nonnull
    private String hashId(String xnatId) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(xnatId.getBytes());
            return Hex.encodeHexString(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to hash xnatId, returning id itself", e);
            return xnatId;
        }
    }
}
