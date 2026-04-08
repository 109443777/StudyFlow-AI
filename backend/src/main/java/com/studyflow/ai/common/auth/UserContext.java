package com.studyflow.ai.common.auth;

public final class UserContext {

    private static final ThreadLocal<LoginUser> USER_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        USER_HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return USER_HOLDER.get();
    }

    public static Long getRequiredUserId() {
        LoginUser loginUser = USER_HOLDER.get();
        return loginUser == null ? null : loginUser.getUserId();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
