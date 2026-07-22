package com.desperadoboi.imagetopdf.ui.about;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.desperadoboi.imagetopdf.R;

public final class AboutFragment extends Fragment {
    public static final String TAG = "AboutFragment";

    private NavigationCallback navigationCallback;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof NavigationCallback)) {
            throw new IllegalStateException("Host activity must implement NavigationCallback");
        }
        navigationCallback = (NavigationCallback) context;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView versionText = view.findViewById(R.id.text_about_version);
        versionText.setText(getString(
                R.string.about_version_format,
                getVersionName()
        ));

        TextView developerText = view.findViewById(R.id.text_about_developer);
        developerText.setText(getString(
                R.string.about_developer_format,
                getString(R.string.developer_name)
        ));
        TextView copyrightText = view.findViewById(R.id.text_about_copyright);
        copyrightText.setText(getString(
                R.string.about_copyright_format,
                getString(R.string.developer_name)
        ));

        view.findViewById(R.id.button_about_back).setOnClickListener(
                ignored -> closeScreen()
        );
        view.findViewById(R.id.button_about_privacy).setOnClickListener(
                ignored -> openLocalPrivacyPolicy()
        );
        view.findViewById(R.id.button_about_email).setOnClickListener(
                ignored -> emailDeveloper()
        );
        view.findViewById(R.id.button_about_privacy_browser).setOnClickListener(
                ignored -> openPrivacyPolicyInBrowser()
        );
    }

    @Override
    public void onDetach() {
        navigationCallback = null;
        super.onDetach();
    }

    private void closeScreen() {
        getParentFragmentManager().popBackStack();
    }

    @SuppressWarnings("deprecation")
    private String getVersionName() {
        Context context = requireContext();
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = packageManager.getPackageInfo(
                        context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            }
            String versionName = packageInfo.versionName;
            return AboutPrivacyFormatter.resolveVersionName(
                    versionName,
                    getString(R.string.about_version_unknown)
            );
        } catch (PackageManager.NameNotFoundException exception) {
            return getString(R.string.about_version_unknown);
        }
    }

    private void openLocalPrivacyPolicy() {
        if (navigationCallback != null) {
            navigationCallback.onPrivacyPolicyRequested();
        }
    }

    private void emailDeveloper() {
        Uri emailUri = Uri.parse(AboutPrivacyFormatter.createMailtoUri(
                getString(R.string.developer_email),
                getString(R.string.developer_email_subject)
        ));
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, emailUri);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.developer_email_subject));
        try {
            startActivity(emailIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showToast(R.string.status_email_app_not_found);
        }
    }

    private void openPrivacyPolicyInBrowser() {
        Intent browserIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(AboutPrivacyFormatter.requireHttpsUri(
                        getString(R.string.privacy_policy_url)
                ))
        );
        try {
            startActivity(browserIntent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            showToast(R.string.status_browser_app_not_found);
        }
    }

    private void showToast(int stringResId) {
        if (isAdded()) {
            Toast.makeText(requireContext(), stringResId, Toast.LENGTH_SHORT).show();
        }
    }

    public interface NavigationCallback {
        void onPrivacyPolicyRequested();
    }
}
