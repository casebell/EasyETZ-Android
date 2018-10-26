package com.etzwallet.tools.adapter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.etzwallet.R;
import com.etzwallet.presenter.customviews.BRText;
import com.etzwallet.presenter.customviews.MyLog;
import com.etzwallet.tools.manager.BRSharedPrefs;
import com.etzwallet.tools.services.SyncService;
import com.etzwallet.tools.threads.executor.BRExecutor;
import com.etzwallet.tools.util.CurrencyUtils;
import com.etzwallet.wallet.WalletsMaster;
import com.etzwallet.wallet.abstracts.BaseWalletManager;

import java.text.NumberFormat;
import java.util.ArrayList;

/**
 * Created by byfieldj on 1/31/18.
 */

public class WalletListAdapter extends RecyclerView.Adapter<WalletListAdapter.WalletItemViewHolder> {

    public static final String TAG = WalletListAdapter.class.getName();

    private final Context mContext;
    private ArrayList<WalletItem> mWalletItems;
    private WalletItem mCurrentWalletSyncing;
    private boolean mObesrverIsStarting;
    private SyncNotificationBroadcastReceiver mSyncNotificationBroadcastReceiver;

    private static final int VIEW_TYPE_WALLET = 0;
    private static final int VIEW_TYPE_ADD_WALLET = 1;

    public WalletListAdapter(Context context, ArrayList<BaseWalletManager> walletList) {
        this.mContext = context;
        mWalletItems = new ArrayList<>();
        for (BaseWalletManager w : walletList) {
            this.mWalletItems.add(new WalletItem(w));
        }

        mSyncNotificationBroadcastReceiver = new SyncNotificationBroadcastReceiver();
    }

    @Override
    public WalletItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
        View convertView;

        if (viewType == VIEW_TYPE_WALLET) {
            convertView = inflater.inflate(R.layout.wallet_list_item, parent, false);
            return new WalletItemViewHolder(convertView);
        } else {
            convertView = inflater.inflate(R.layout.add_wallets_item, parent, false);
            return new AddWalletItemViewHolder(convertView);

        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position < mWalletItems.size()) {
            return VIEW_TYPE_WALLET;
        } else {
            return VIEW_TYPE_ADD_WALLET;
        }
    }

    public BaseWalletManager getItemAt(int pos) {
        if (pos < mWalletItems.size()) {
            return mWalletItems.get(pos).walletManager;
        }

        return null;
    }

    @Override
    public void onBindViewHolder(final WalletItemViewHolder holder, int position) {
        MyLog.d( "onBindViewHolder");

        if (getItemViewType(position) == VIEW_TYPE_WALLET) {

            WalletItem item = mWalletItems.get(position);
            final BaseWalletManager wallet = item.walletManager;

            String name = wallet.getName();
            String iso = wallet.getIso();


            String fiatBalance = CurrencyUtils.getFormattedAmount(mContext, BRSharedPrefs.getPreferredFiatIso(mContext), wallet.getFiatBalance(mContext));
            String cryptoBalance = CurrencyUtils.getFormattedAmount(mContext, wallet.getIso(), wallet.getCachedBalance(mContext));

            // Set wallet fields
            holder.mWalletName.setText(name);

//            if(iso.equalsIgnoreCase("BO")){
//                String exchangeRate = "0";
//                holder.mTradePrice.setText(mContext.getString(R.string.Account_exchangeRate, exchangeRate, iso));
//                holder.mWalletBalanceFiat.setText("0");
//            }else{
//                String exchangeRate = CurrencyUtils.getFormattedAmount(mContext, BRSharedPrefs.getPreferredFiatIso(mContext), wallet.getFiatExchangeRate(mContext));
//                holder.mTradePrice.setText(mContext.getString(R.string.Account_exchangeRate, exchangeRate, iso));
//                holder.mWalletBalanceFiat.setText(fiatBalance);
//            }

            String exchangeRate = CurrencyUtils.getFormattedAmount(mContext, BRSharedPrefs.getPreferredFiatIso(mContext), wallet.getFiatExchangeRate(mContext));
            holder.mTradePrice.setText(mContext.getString(R.string.Account_exchangeRate, exchangeRate, iso));
            holder.mWalletBalanceFiat.setText(fiatBalance);


            holder.mWalletBalanceFiat.setTextColor(mContext.getResources().getColor(item.mShowSyncProgress ? R.color.wallet_balance_fiat_syncing : R.color.wallet_balance_fiat));
            holder.mWalletBalanceCurrency.setText(cryptoBalance);
            holder.mWalletBalanceCurrency.setVisibility(!item.mShowSyncProgress ? View.VISIBLE : View.INVISIBLE);
            holder.mSyncingProgressBar.setVisibility(item.mShowSyncProgress ? View.VISIBLE : View.INVISIBLE);
            holder.mSyncingLabel.setVisibility(item.mShowSyncProgress ? View.VISIBLE : View.INVISIBLE);
            holder.mSyncingLabel.setText(item.mLabelText);

            String startColor = wallet.getUiConfiguration().getStartColor();
            String endColor = wallet.getUiConfiguration().getEndColor();

        Drawable drawable = mContext.getResources().getDrawable(R.drawable.crypto_card_shape, null).mutate();
        //create gradient with 2 colors if exist
        ((GradientDrawable) drawable).setColors(new int[]{Color.parseColor(startColor), Color.parseColor(endColor == null ? startColor : endColor)});
        ((GradientDrawable) drawable).setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        holder.mParent.setBackground(drawable);

        }
    }

    public void stopObserving() {
        SyncService.unregisterSyncNotificationBroadcastReceiver(mContext.getApplicationContext(), mSyncNotificationBroadcastReceiver);
    }

    public void startObserving() {
        if (mObesrverIsStarting) {
            return;
        }
        mObesrverIsStarting = true;

        SyncService.registerSyncNotificationBroadcastReceiver(mContext.getApplicationContext(), mSyncNotificationBroadcastReceiver);

        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mCurrentWalletSyncing = getNextWalletToSync();
                    if (mCurrentWalletSyncing == null) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                for (WalletItem item : mWalletItems) {
                                    item.updateData(false);
                                }
                            }
                        });

                        return;
                    }
                    String walletIso = mCurrentWalletSyncing.walletManager.getIso();
                    mCurrentWalletSyncing.walletManager.connect(mContext);
                    SyncService.startService(mContext.getApplicationContext(), SyncService.ACTION_START_SYNC_PROGRESS_POLLING, walletIso);
                } finally {
                    mObesrverIsStarting = false;
                }

            }
        });

    }

    private boolean updateUi(WalletItem currentWallet, double syncProgress) {
        if (mCurrentWalletSyncing == null || mCurrentWalletSyncing.walletManager == null) {
            MyLog.e( "run: should not happen but ok, ignore it.");
            return false;
        }
        if (syncProgress > SyncService.PROGRESS_START && syncProgress < SyncService.PROGRESS_FINISH) {
//            MyLog.d( "ISO: " + currentWallet.walletManager.getIso(mContext) + " (" + progress + "%)");
            StringBuffer labelText = new StringBuffer(mContext.getString(R.string.SyncingView_syncing));
            labelText.append(' ')
                    .append(NumberFormat.getPercentInstance().format(syncProgress));

            mCurrentWalletSyncing.updateData(true, labelText.toString());
        } else if (syncProgress == SyncService.PROGRESS_FINISH) {
//            MyLog.d( "ISO: " + currentWallet.walletManager.getIso(mContext) + " (100%)");

            //Done should not be seen but if it is because of a bug or something, then let if be a decent explanation
            mCurrentWalletSyncing.updateData(false);

            //start from beginning
            startObserving();
            return false;

        }
        return true;
    }

    //return the next wallet that is not connected or null if all are connected
    private WalletItem getNextWalletToSync() {
        BaseWalletManager currentWallet = WalletsMaster.getInstance(mContext).getCurrentWallet(mContext);
        if (currentWallet != null && currentWallet.getSyncProgress(BRSharedPrefs.getStartHeight(mContext, currentWallet.getIso())) == 1) {
            currentWallet = null;
        }

        for (WalletItem w : mWalletItems) {
            if (currentWallet == null) {
                if (w.walletManager.getSyncProgress(BRSharedPrefs.getStartHeight(mContext, w.walletManager.getIso())) < 1
                        || w.walletManager.getConnectStatus() != 2) {
                    w.walletManager.connect(mContext);
                    return w;
                }
            } else {
                if (w.walletManager.getIso().equalsIgnoreCase(currentWallet.getIso())) {
                    return w;
                }
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return mWalletItems.size() + 1;
    }

    public class WalletItemViewHolder extends RecyclerView.ViewHolder {

        private BRText mWalletName;
        private BRText mTradePrice;
        private BRText mWalletBalanceFiat;
        private BRText mWalletBalanceCurrency;
        private RelativeLayout mParent;
        private BRText mSyncingLabel;
        private ProgressBar mSyncingProgressBar;

        public WalletItemViewHolder(View view) {
            super(view);

            mWalletName = view.findViewById(R.id.wallet_name);
            mTradePrice = view.findViewById(R.id.wallet_trade_price);
            mWalletBalanceFiat = view.findViewById(R.id.wallet_balance_fiat);
            mWalletBalanceCurrency = view.findViewById(R.id.wallet_balance_currency);
            mParent = view.findViewById(R.id.wallet_card);//Home页面 添加
            mSyncingLabel = view.findViewById(R.id.syncing_label);
            mSyncingProgressBar = view.findViewById(R.id.sync_progress);
        }
    }

    public class AddWalletItemViewHolder extends WalletItemViewHolder {

        public AddWalletItemViewHolder(View view) {
            super(view);
        }
    }

    private class WalletItem {
        public BaseWalletManager walletManager;
        private boolean mShowSyncProgress = false;
        private String mLabelText;

        WalletItem(BaseWalletManager walletManager) {
            this.walletManager = walletManager;
        }

        public void updateData(boolean showSyncProgress) {
           updateData(showSyncProgress, null);
        }

        public void updateData(boolean showSyncProgress, String labelText) {
            mShowSyncProgress = showSyncProgress;

            if (labelText != null) {
                mLabelText = labelText;
            }

            notifyDataSetChanged();
        }
    }

    /**
     * The {@link SyncNotificationBroadcastReceiver} is responsible for receiving updates from the
     * {@link SyncService} and updating the UI accordingly.
     */
    private class SyncNotificationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SyncService.ACTION_SYNC_PROGRESS_UPDATE.equals(intent.getAction())) {
                String intentWalletIso = intent.getStringExtra(SyncService.EXTRA_WALLET_ISO);
                double progress = intent.getDoubleExtra(SyncService.EXTRA_PROGRESS, SyncService.PROGRESS_NOT_DEFINED);

                if (mCurrentWalletSyncing == null) {
                    MyLog.e( "SyncNotificationBroadcastReceiver.onReceive: mCurrentWalletSyncing is null. Wallet:" + intentWalletIso + " Progress:" + progress + " Ignored");
                    return;
                }

                String currentWalletISO = mCurrentWalletSyncing.walletManager.getIso();
                if (currentWalletISO.equals(intentWalletIso)) {
                    if (progress >= SyncService.PROGRESS_START) {
                        updateUi(mCurrentWalletSyncing, progress);
                    } else {
                        MyLog.e( "SyncNotificationBroadcastReceiver.onReceive: Progress not set:" + progress);
                    }
                } else {
                    MyLog.e( "SyncNotificationBroadcastReceiver.onReceive: Wrong wallet. Expected:" + currentWalletISO + " Actual:" + intentWalletIso + " Progress:" + progress);
                }
            }
        }
    }
}
