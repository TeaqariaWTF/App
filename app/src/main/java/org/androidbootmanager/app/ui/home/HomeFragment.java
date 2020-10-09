package org.androidbootmanager.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.util.Constants;

import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        TextView statusText0 = root.findViewById(R.id.home_installedWorking_superUser);
        TextView statusText1 = root.findViewById(R.id.home_installedWorking_install1);
        TextView statusText2 = root.findViewById(R.id.home_installedWorking_install2);
        TextView statusText3 = root.findViewById(R.id.home_installedWorking_install3);
        AtomicBoolean check0 = new AtomicBoolean(false);
        AtomicBoolean check1 = new AtomicBoolean(false);
        AtomicBoolean check2 = new AtomicBoolean(false);
        boolean check3;
        ImageView statusImg = root.findViewById(R.id.home_installedWorking_image);
        Shell.su(Constants.scriptDir + "is_installed.sh").submit((result) -> {
            check0.set(Shell.rootAccess());
            statusText0.setText(getString(R.string.superuser) + " " + getString(check0.get() ? R.string.ok : R.string.failure));
            check1.set(result.getCode() == 0);
            statusText1.setText(getString(R.string.installcheck1) + " " + getString(check1.get() ? R.string.ok : R.string.failure));
            check2.set(result.getOut().contains("ABM.bootloader=1"));
            statusText2.setText(getString(R.string.installcheck2) + " " + getString(check2.get() ? R.string.ok : R.string.failure));
        });
        return root;
    }
}