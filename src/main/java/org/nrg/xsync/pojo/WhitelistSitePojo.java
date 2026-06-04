package org.nrg.xsync.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class WhitelistSitePojo {
    private String siteId;
    private String siteName;
    private String siteUrl;
    private String classification;
}
