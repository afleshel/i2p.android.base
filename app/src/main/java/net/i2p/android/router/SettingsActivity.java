package net.i2p.android.router;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import net.i2p.I2PAppContext;
import net.i2p.android.router.R;
import net.i2p.android.router.service.StatSummarizer;
import net.i2p.android.router.util.Util;
import net.i2p.router.RouterContext;
import net.i2p.stat.FrequencyStat;
import net.i2p.stat.Rate;
import net.i2p.stat.RateStat;
import net.i2p.stat.StatManager;
import net.i2p.util.LogManager;
import net.i2p.util.OrderedProperties;

public class SettingsActivity extends PreferenceActivity {
    // Actions for legacy settings
    private static final String ACTION_PREFS_NET = "net.i2p.android.router.PREFS_NET";
    private static final String ACTION_PREFS_GRAPHS = "net.i2p.android.router.PREFS_GRAPHS";
    private static final String ACTION_PREFS_LOGGING = "net.i2p.android.router.PREFS_LOGGING";
    private static final String ACTION_PREFS_ADVANCED = "net.i2p.android.router.PREFS_ADVANCED";

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent().getAction();
        if (action != null) {
            if (ACTION_PREFS_NET.equals(action)) {
                addPreferencesFromResource(R.xml.settings_net);
            } else if (ACTION_PREFS_GRAPHS.equals(action)){
                addPreferencesFromResource(R.xml.settings_graphs);
                setupGraphSettings(this, getPreferenceScreen(), getRouterContext());
            } else if (ACTION_PREFS_LOGGING.equals(action)) {
                addPreferencesFromResource(R.xml.settings_logging);
                RouterContext ctx = getRouterContext();
                if (ctx != null)
                    setupLoggingSettings(this, getPreferenceScreen(), ctx);
            } else if (ACTION_PREFS_ADVANCED.equals(action)) {
                addPreferencesFromResource(R.xml.settings_advanced);
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            // Load the legacy preferences headers
            addPreferencesFromResource(R.xml.settings_headers_legacy);
        }
    }

    protected static RouterContext getRouterContext() {
        List<RouterContext> contexts = RouterContext.listContexts();
        if ( !((contexts == null) || (contexts.isEmpty())) ) {
            return contexts.get(0);
        }
        return null;
    }

    protected static void setupGraphSettings(Context context, PreferenceScreen ps, RouterContext ctx) {
        if (ctx == null) {
            PreferenceCategory noRouter = new PreferenceCategory(context);
            noRouter.setTitle(R.string.router_not_running);
            ps.addPreference(noRouter);
        } else if (StatSummarizer.instance() == null) {
            PreferenceCategory noStats = new PreferenceCategory(context);
            noStats.setTitle(R.string.stats_not_ready);
            ps.addPreference(noStats);
        } else {
            StatManager mgr = ctx.statManager();
            Map<String, SortedSet<String>> all = mgr.getStatsByGroup();
            for (String group : all.keySet()) {
                SortedSet<String> stats = all.get(group);
                if (stats.size() == 0) continue;
                PreferenceCategory groupPrefs = new PreferenceCategory(context);
                groupPrefs.setKey("stat.groups." + group);
                groupPrefs.setTitle(group);
                ps.addPreference(groupPrefs);
                for (String stat : stats) {
                    String key;
                    String description;
                    boolean canBeGraphed = false;
                    boolean currentIsGraphed = false;
                    RateStat rs = mgr.getRate(stat);
                    if (rs != null) {
                        description = rs.getDescription();
                        long period = rs.getPeriods()[0]; // should be the minimum
                        key = stat + "." + period;
                        if (period <= 10*60*1000) {
                            Rate r = rs.getRate(period);
                            canBeGraphed = r != null;
                            if (canBeGraphed) {
                                currentIsGraphed = r.getSummaryListener() != null;
                            }
                        }
                    } else {
                        FrequencyStat fs = mgr.getFrequency(stat);
                        if (fs != null) {
                            key = stat;
                            description = fs.getDescription();
                            // FrequencyStats cannot be graphed, but can be logged.
                            // XXX: Should log settings be here as well, or in a
                            // separate settings menu?
                        } else {
                            Util.e("Stat does not exist?!  [" + stat + "]");
                            continue;
                        }
                    }
                    CheckBoxPreference statPref = new CheckBoxPreference(context);
                    statPref.setKey("stat.summaries." + key);
                    statPref.setTitle(stat);
                    statPref.setSummary(description);
                    statPref.setEnabled(canBeGraphed);
                    statPref.setChecked(currentIsGraphed);
                    groupPrefs.addPreference(statPref);
                }
            }
        }
    }

    protected static void setupLoggingSettings(Context context, PreferenceScreen ps, RouterContext ctx) {
        if (ctx != null) {
            LogManager mgr = ctx.logManager();
            // Log level overrides
            /*
            StringBuilder buf = new StringBuilder(32*1024);
            Properties limits = mgr.getLimits();
            TreeSet<String> sortedLogs = new TreeSet<String>();
            for (Iterator iter = limits.keySet().iterator(); iter.hasNext(); ) {
                String prefix = (String)iter.next();
                sortedLogs.add(prefix);
            }
            for (Iterator iter = sortedLogs.iterator(); iter.hasNext(); ) {
                String prefix = (String)iter.next();
                String level = limits.getProperty(prefix);
                buf.append(prefix).append('=').append(level).append('\n');
            }
            */
        } else {
            PreferenceCategory noRouter = new PreferenceCategory(context);
            noRouter.setTitle(R.string.router_not_running);
            ps.addPreference(noRouter);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onBuildHeaders(List<Header> target) {
        // The resource com.android.internal.R.bool.preferences_prefer_dual_pane
        // has different definitions based upon screen size. At present, it will
        // be true for -sw720dp devices, false otherwise. For your curiosity, in
        // Nexus 7 it is false.
        loadHeadersFromResource(R.xml.settings_headers, target);
    }

    @Override
    protected void onPause() {
        List<Properties> lProps = Util.getPropertiesFromPreferences(this);
        Properties props = lProps.get(0);
        Properties logSettings = lProps.get(1);

        // Apply new config if we are running.
        List<RouterContext> contexts = RouterContext.listContexts();
        if ( !((contexts == null) || (contexts.isEmpty())) ) {
            RouterContext _context = contexts.get(0);
            _context.router().saveConfig(props, null);

            // Merge in new log settings
            saveLoggingChanges(_context, logSettings);
        } else {
            // Merge in new config settings, write the file.
            InitActivities init = new InitActivities(this);
            init.mergeResourceToFile(R.raw.router_config, "router.config", props);

            // Merge in new log settings
            saveLoggingChanges(I2PAppContext.getGlobalContext(), logSettings);
        }

        // Store the settings in Android
        super.onPause();
    }

    private void saveLoggingChanges(I2PAppContext ctx, Properties logSettings) {
        boolean shouldSave = false;

        for (Object key : logSettings.keySet()) {
            if ("logger.defaultLevel".equals(key)) {
                String defaultLevel = (String) logSettings.get(key);
                String oldDefault = ctx.logManager().getDefaultLimit();
                if (!defaultLevel.equals(oldDefault)) {
                    shouldSave = true;
                    ctx.logManager().setDefaultLimit(defaultLevel);
                }
            }
        }

        if (shouldSave) {
            ctx.logManager().saveConfig();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            String settings = getArguments().getString("settings");
            if ("net".equals(settings)) {
                addPreferencesFromResource(R.xml.settings_net);
            } else if ("graphs".equals(settings)) {
                addPreferencesFromResource(R.xml.settings_graphs);
                RouterContext ctx = getRouterContext();
                if (ctx != null)
                    setupGraphSettings(getActivity(), getPreferenceScreen(), ctx);
            } else if ("logging".equals(settings)) {
                addPreferencesFromResource(R.xml.settings_logging);
                RouterContext ctx = getRouterContext();
                if (ctx != null)
                    setupLoggingSettings(getActivity(), getPreferenceScreen(), ctx);
            } else if ("advanced".equals(settings)) {
                addPreferencesFromResource(R.xml.settings_advanced);
            }
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return SettingsFragment.class.getName().equals(fragmentName);
    }
}