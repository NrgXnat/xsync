package org.nrg.xnat.xsync.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.nrg.xft.XFTItem;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.configuration.ProjectSyncConfiguration;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
@Slf4j
public class RandomLabelGenerator implements XsyncLabelGeneratorI {
    /**
     * Generate new label for data based on random alphanum
     * @param user the user
     * @param item the item
     * @param projectSyncConfiguration the project sync configuration
     * @return the new label or null if not able to assign one
     */
    @Nullable
    @Override
    public String generateId(UserI user, XFTItem item, ProjectSyncConfiguration projectSyncConfiguration) {
        RandomStringGenerator rsg = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
                .build();
        return rsg.generate(12);
    }
}
