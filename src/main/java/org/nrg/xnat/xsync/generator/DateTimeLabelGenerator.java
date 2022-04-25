package org.nrg.xnat.xsync.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.model.XnatExperimentdataI;
import org.nrg.xdat.model.XnatSubjectassessordataI;
import org.nrg.xdat.om.*;
import org.nrg.xft.ItemI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class DateTimeLabelGenerator implements XsyncLabelGeneratorI {
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
            XnatSubjectdata targetSubject = (XnatSubjectdata) target;
            List<XnatSubjectassessordataI> expts = targetSubject.getExperiments_experiment();
            if (!expts.isEmpty()) {
                return expts.stream().map(this::getDateTimeStringFromExpt)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(getDateTimeStringFromSubject(targetSubject));
            } else {
                return getDateTimeStringFromSubject(targetSubject);
            }
        } else if (target instanceof XnatExperimentdataI) {
            return getDateTimeStringFromExpt((XnatExperimentdataI) target);
        } else {
            log.warn("Unable to generate label for target because it is not a subject or experiment: {}", target);
            return null;
        }
    }

    @Nullable
    private String getDateTimeStringFromSubject(XnatSubjectdata targetSubject) {
        Date date = targetSubject.getInsertDate();
        return date != null ? fullDateFormat.format(date) : null;
    }

    @Nullable
    private String getDateTimeStringFromExpt(XnatExperimentdataI targetExperiment) {
        Date date = (Date) targetExperiment.getDate();
        Date time = (Date) targetExperiment.getTime();
        String label = date != null ? dateFormat.format(date) : "";
        if (time != null) {
            label += timeFormat.format(time);
        }
        return StringUtils.defaultIfBlank(label, null);
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    private static final DateFormat timeFormat = new SimpleDateFormat("HHmmssSSS");
    private static final DateFormat fullDateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
}
