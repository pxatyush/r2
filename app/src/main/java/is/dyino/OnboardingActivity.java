package is.dyino;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import is.dyino.util.AppPrefs;

public class OnboardingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Skip if not first run
        AppPrefs prefs = new AppPrefs(this);
        if (!prefs.isFirstRun()) {
            startMain(); return;
        }

        getWindow().setStatusBarColor(0xFF0D0D14);
        getWindow().setNavigationBarColor(0xFF0D0D14);
        setContentView(R.layout.activity_onboarding);

        findViewById(R.id.btnGetStarted).setOnClickListener(v -> {
            prefs.setFirstRunDone();
            startMain();
        });
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
