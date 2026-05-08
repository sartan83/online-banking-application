package com.devilsvault.api.user;

/**
 * Sealed classification of user roles used by the access policy.
 *
 * <p>The legacy stack split users into two physical tables (internal_user vs external_users); on
 * the modern stack we keep a single {@code app_user} table with a {@link Role} enum and use this
 * sealed hierarchy to express the same internal-versus-external distinction at compile time.
 */
public sealed interface UserCategory {

    String label();

    /** Bank staff: EMPLOYEE, MANAGER, ADMIN. */
    record Internal() implements UserCategory {
        @Override
        public String label() {
            return "internal";
        }
    }

    /** Bank counter-parties: CUSTOMER, MERCHANT. */
    record External() implements UserCategory {
        @Override
        public String label() {
            return "external";
        }
    }

    UserCategory INTERNAL = new Internal();
    UserCategory EXTERNAL = new External();
}
