package com.yizhaoqi.smartpai.security;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户访问范围，用于统一所有检索路径的 ACL。
 * 不可为空；scope 为空时 fail-closed。
 */
public class AccessScope {

    private final String userDbId;
    private final List<String> orgTags;
    private final boolean publicOnly;

    private AccessScope(String userDbId, List<String> orgTags, boolean publicOnly) {
        this.userDbId = userDbId;
        this.orgTags = orgTags != null ? List.copyOf(orgTags) : Collections.emptyList();
        this.publicOnly = publicOnly;
    }

    /** 匿名用户：仅公开文档 */
    public static AccessScope anonymous() {
        return new AccessScope(null, Collections.emptyList(), true);
    }

    /** 认证用户：本人文档 + 公开文档 + 组织标签文档 */
    public static AccessScope authenticated(String userDbId, List<String> orgTags) {
        Objects.requireNonNull(userDbId, "userDbId must not be null for authenticated scope");
        return new AccessScope(userDbId, orgTags, false);
    }

    /** 仅公开文档（管理员查看所有公开内容） */
    public static AccessScope publicOnly() {
        return new AccessScope(null, Collections.emptyList(), true);
    }

    public String getUserDbId() {
        return userDbId;
    }

    public List<String> getOrgTags() {
        return orgTags;
    }

    public boolean isPublicOnly() {
        return publicOnly;
    }

    public boolean isAnonymous() {
        return userDbId == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessScope that)) return false;
        return publicOnly == that.publicOnly
                && Objects.equals(userDbId, that.userDbId)
                && Objects.equals(orgTags, that.orgTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userDbId, orgTags, publicOnly);
    }
}
