package org.nrg.xsync.pojo.history;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XsyncAssessorHistoryPojo {
    private String localLabel;
    private String subjectLabel;
    private String experimentLabel;
    private String syncStatus;
    private String syncMessage;
}
