package kz.aitu.auth.config;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Data structure representing local AITU authentication session in .minecraft/config/aitu_session.json
 */
public class SessionData {

    @SerializedName("session_token")
    private String sessionToken;

    @SerializedName("cached_nickname")
    private String cachedNickname;

    @SerializedName("telegram_id")
    private long telegramId;

    public SessionData() {
    }

    public SessionData(String sessionToken, String cachedNickname, long telegramId) {
        this.sessionToken = sessionToken != null ? sessionToken.trim() : null;
        this.cachedNickname = cachedNickname != null ? cachedNickname.trim() : null;
        this.telegramId = telegramId;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getCachedNickname() {
        return cachedNickname;
    }

    public void setCachedNickname(String cachedNickname) {
        this.cachedNickname = cachedNickname;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(long telegramId) {
        this.telegramId = telegramId;
    }

    /**
     * Checks if this session object contains valid, non-empty data.
     */
    public boolean isValid() {
        return sessionToken != null && !sessionToken.trim().isEmpty()
                && cachedNickname != null && !cachedNickname.trim().isEmpty()
                && telegramId != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionData that = (SessionData) o;
        return telegramId == that.telegramId &&
                Objects.equals(sessionToken, that.sessionToken) &&
                Objects.equals(cachedNickname, that.cachedNickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionToken, cachedNickname, telegramId);
    }

    @Override
    public String toString() {
        return "SessionData{" +
                "sessionToken='" + (sessionToken != null ? "[PROTECTED]" : "null") + '\'' +
                ", cachedNickname='" + cachedNickname + '\'' +
                ", telegramId=" + telegramId +
                '}';
    }
}
