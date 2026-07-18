package android.net;

import android.os.Parcel;

import java.util.Collections;
import java.util.List;

public final class FakeUri extends Uri {
    private final String value;

    private FakeUri(String value) {
        this.value = value;
    }

    public static Uri create(String value) {
        return new FakeUri(value);
    }

    @Override
    public Builder buildUpon() {
        return null;
    }

    @Override
    public String getAuthority() {
        return null;
    }

    @Override
    public String getEncodedAuthority() {
        return null;
    }

    @Override
    public String getEncodedFragment() {
        return null;
    }

    @Override
    public String getEncodedPath() {
        return null;
    }

    @Override
    public String getEncodedQuery() {
        return null;
    }

    @Override
    public String getEncodedSchemeSpecificPart() {
        return value;
    }

    @Override
    public String getEncodedUserInfo() {
        return null;
    }

    @Override
    public String getFragment() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }

    @Override
    public String getLastPathSegment() {
        return null;
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public List<String> getPathSegments() {
        return Collections.emptyList();
    }

    @Override
    public int getPort() {
        return -1;
    }

    @Override
    public String getQuery() {
        return null;
    }

    @Override
    public String getScheme() {
        return "content";
    }

    @Override
    public String getSchemeSpecificPart() {
        return value;
    }

    @Override
    public String getUserInfo() {
        return null;
    }

    @Override
    public boolean isHierarchical() {
        return true;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public String toString() {
        return value;
    }
}
