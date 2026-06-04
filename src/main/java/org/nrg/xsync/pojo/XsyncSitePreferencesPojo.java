package org.nrg.xsync.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class XsyncSitePreferencesPojo {

   public XsyncSitePreferencesPojo() {}

   public XsyncSitePreferencesPojo(final String tokenRefreshInterval,
                                   final String syncRetryInterval,
                                   final String syncRetryCount,
                                   final String syncMaxUncompressedZipFileSize,
                                   final Boolean xsyncWhitelistEnabled) {
         this.tokenRefreshInterval = tokenRefreshInterval;
         this.syncRetryInterval = syncRetryInterval;
         this.syncRetryCount = syncRetryCount;
         this.syncMaxUncompressedZipFileSize = syncMaxUncompressedZipFileSize;
         this.xsyncWhitelistEnabled = xsyncWhitelistEnabled;
   }

   private String  tokenRefreshInterval;
   private String  syncRetryInterval;
   private String  syncRetryCount;
   private String  syncMaxUncompressedZipFileSize;
   private Boolean  xsyncWhitelistEnabled;
}
