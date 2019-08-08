package io.stormbird.wallet.viewmodel;

import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.text.TextUtils;
import android.util.Log;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.stormbird.wallet.C;
import io.stormbird.wallet.entity.*;
import io.stormbird.wallet.interact.ExportWalletInteract;
import io.stormbird.wallet.interact.FetchWalletsInteract;
import io.stormbird.wallet.service.KeyService;

public class BackupKeyViewModel extends BaseViewModel {
    private final static String TAG = BackupKeyViewModel.class.getSimpleName();

    private final KeyService keyService;
    private final ExportWalletInteract exportWalletInteract;
    private final FetchWalletsInteract fetchWalletsInteract;

    private final MutableLiveData<Wallet> saved = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleted = new MutableLiveData<>();
    private final MutableLiveData<String> exportedStore = new MutableLiveData<>();
    private final MutableLiveData<ErrorEnvelope> exportWalletError = new MutableLiveData<>();
    private final MutableLiveData<ErrorEnvelope> deleteWalletError = new MutableLiveData<>();

    public BackupKeyViewModel(
            KeyService keyService,
            ExportWalletInteract exportWalletInteract,
            FetchWalletsInteract fetchWalletsInteract) {
        this.keyService = keyService;
        this.exportWalletInteract = exportWalletInteract;
        this.fetchWalletsInteract = fetchWalletsInteract;
    }

    public LiveData<ErrorEnvelope> exportWalletError() {
        return exportWalletError;
    }

    public LiveData<ErrorEnvelope> deleteWalletError() {
        return deleteWalletError;
    }

    public LiveData<Boolean> deleted() {
        return deleted;
    }

    public LiveData<Wallet> saved() {
        return saved;
    }

    public LiveData<String> exportedStore() {
        return exportedStore;
    }

    private void onDeleteWalletError(Throwable throwable) {
        deleteWalletError.postValue(
                new ErrorEnvelope(C.ErrorCode.UNKNOWN, TextUtils.isEmpty(throwable.getLocalizedMessage())
                        ? throwable.getMessage() : throwable.getLocalizedMessage()));
    }

    private void onDelete(Wallet[] wallets) {
        deleted.postValue(true);
    }

    public void exportWallet(String walletAddr, String keystorePassword, String storePassword) {
        disposable = exportWalletInteract
                .export(new Wallet(walletAddr), keystorePassword, storePassword)
                .subscribe(exportedStore::postValue, this::onExportWalletError);
    }

    private void onExportWalletError(Throwable throwable) {
        exportWalletError.postValue(
                new ErrorEnvelope(C.ErrorCode.UNKNOWN, TextUtils.isEmpty(throwable.getLocalizedMessage())
                        ? throwable.getMessage() : throwable.getLocalizedMessage()));
    }

    public void storeWallet(Wallet wallet) {
        disposable = fetchWalletsInteract.storeWallet(wallet)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(saved::postValue, this::onError);
    }

    @Override
    protected void onError(Throwable throwable) {
        super.onError(throwable);
    }


    public void upgradeWallet(String keyAddress)
    {
        fetchWalletsInteract.getWallet(keyAddress)
                .map(this::upgradeWallet)
                .flatMap(fetchWalletsInteract::storeWallet)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onWalletUpgraded, this::onError)
                .isDisposed();
    }

    private void onWalletUpgraded(Wallet wallet)
    {
        Log.d("HVM", "Wallet " + wallet.address + " Upgraded to: " + wallet.authLevel.toString());
    }

    private Wallet upgradeWallet(Wallet wallet)
    {
        switch (wallet.authLevel)
        {
            default:
                break;
            case NOT_SET:
                if (wallet.type == WalletType.HDKEY) wallet.authLevel = KeyService.AuthenticationLevel.TEE_AUTHENTICATION;
                break;
            case TEE_NO_AUTHENTICATION:
                wallet.authLevel = KeyService.AuthenticationLevel.TEE_AUTHENTICATION;
                break;
            case STRONGBOX_NO_AUTHENTICATION:
                wallet.authLevel = KeyService.AuthenticationLevel.STRONGBOX_AUTHENTICATION;
                break;
        }

        return wallet;
    }


    public KeyService.UpgradeKeyResult upgradeKeySecurity(String keyBackup, Activity activity, SignAuthenticationCallback callback)
    {
        return keyService.upgradeKeySecurity(keyBackup, activity, callback);
    }

    public void getPasswordForKeystore(String keyBackup, Activity activity, CreateWalletCallbackInterface callback)
    {
        keyService.getPassword(keyBackup, activity, callback);
    }

    public void getSeedPhrase(String keyBackup, Activity activity, CreateWalletCallbackInterface callback)
    {
        keyService.getMnemonic(keyBackup, activity, callback);
    }

    public void backupSuccess(String keyBackup)
    {
        fetchWalletsInteract.updateBackupTime(keyBackup).isDisposed();
    }
}

