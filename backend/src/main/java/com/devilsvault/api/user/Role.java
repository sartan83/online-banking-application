package com.devilsvault.api.user;

public enum Role {
    CUSTOMER(UserCategory.EXTERNAL),
    MERCHANT(UserCategory.EXTERNAL),
    EMPLOYEE(UserCategory.INTERNAL),
    MANAGER(UserCategory.INTERNAL),
    ADMIN(UserCategory.INTERNAL);

    private final UserCategory category;

    Role(UserCategory category) {
        this.category = category;
    }

    public UserCategory category() {
        return category;
    }

    public boolean isInternal() {
        return category instanceof UserCategory.Internal;
    }

    public boolean isExternal() {
        return category instanceof UserCategory.External;
    }
}
