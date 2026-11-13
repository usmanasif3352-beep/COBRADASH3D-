import crypto from 'node:crypto';
import { GoogleAuth } from 'google-auth-library';
import { FieldValue } from 'firebase-admin/firestore';
import { ApiError } from '../middleware/errorHandler.js';
import { STORE_PRODUCTS } from '../game/catalog.js';

export class PurchaseService {
  constructor({ db, config, playerService, playVerifier = null }) {
    this.db = db;
    this.config = config;
    this.playerService = playerService;
    this.playVerifier = playVerifier || ((args) => this.#verifyWithGooglePlayApi(args));
  }

  async #verifyWithGooglePlayApi({ packageName, productId, purchaseToken, productType }) {
    const credentials = this.config.googlePlayServiceAccountJson
      ? JSON.parse(this.config.googlePlayServiceAccountJson)
      : undefined;

    const auth = new GoogleAuth({
      ...(credentials ? { credentials } : {}),
      scopes: ['https://www.googleapis.com/auth/androidpublisher']
    });
    const client = await auth.getClient();

    const url =
      productType === 'SUBSCRIPTION'
        ? `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(
            packageName
          )}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`
        : `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(
            packageName
          )}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;

    const res = await client.request({ url, method: 'GET' });
    const data = res.data;

    if (productType === 'SUBSCRIPTION') {
      const state = data?.subscriptionState;
      const valid = state === 'SUBSCRIPTION_STATE_ACTIVE' || state === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD';
      return { valid, orderId: data?.latestOrderId || null, rawState: state };
    }

    // purchaseState === 0 means Purchased in Google Play Developer API
    const valid = data?.purchaseState === 0;
    return { valid, orderId: data?.orderId || null, consumptionState: data?.consumptionState };
  }

  async verifyAndFulfillPurchase({ uid, productId, purchaseToken }) {
    const sku = STORE_PRODUCTS[productId];
    if (!sku) {
      throw new ApiError(400, 'UNKNOWN_PRODUCT', `Unsupported productId: ${productId}`);
    }
    if (!purchaseToken || typeof purchaseToken !== 'string' || purchaseToken.length < 12) {
      throw new ApiError(400, 'INVALID_PURCHASE_TOKEN', 'Valid Google Play purchaseToken is required');
    }

    const tokenHash = crypto.createHash('sha256').update(`${productId}:${purchaseToken}`).digest('hex');
    const receiptRef = this.db.collection('verified_purchases').document(tokenHash);

    // Check replay protection before hitting Google Play API
    const existingSnap = await receiptRef.get();
    if (existingSnap.exists) {
      const existing = existingSnap.data();
      if (existing.uid === uid) {
        return { status: 'ALREADY_VERIFIED', productId, orderId: existing.orderId || null };
      }
      throw new ApiError(409, 'PURCHASE_TOKEN_ALREADY_USED', 'Purchase token was already redeemed by another account');
    }

    // Verify receipt with Google Play Developer API
    let verification;
    try {
      verification = await this.playVerifier({
        packageName: this.config.androidPackageName,
        productId,
        purchaseToken,
        productType: sku.type
      });
    } catch (err) {
      throw new ApiError(502, 'PLAY_VERIFICATION_FAILED', `Play Developer API check failed: ${err?.message || err}`);
    }

    if (!verification?.valid) {
      throw new ApiError(402, 'PURCHASE_NOT_VALID', 'Google Play reported purchase as invalid, cancelled, or pending');
    }

    const walletRef = this.db.collection('wallets').document(uid);
    const userRef = this.db.collection('users').document(uid);
    const txRef = this.db.collection('currency_transactions').document();

    const result = await this.db.runTransaction(async (tx) => {
      const [receiptCheck, walletSnap, userSnap] = await Promise.all([
        tx.get(receiptRef),
        tx.get(walletRef),
        tx.get(userRef)
      ]);
      if (receiptCheck.exists) {
        throw new ApiError(409, 'PURCHASE_TOKEN_ALREADY_USED', 'Duplicate purchase redemption');
      }

      const wData = walletSnap.exists
        ? walletSnap.data()
        : { uid, coins: 250, gems: 10, vipActive: false, battlePassPremium: false, adsRemoved: false };

      const nextCoins = (wData.coins || 0) + (sku.coins || 0);
      const nextGems = (wData.gems || 0) + (sku.gems || 0);

      tx.set(receiptRef, {
        tokenHash,
        uid,
        productId,
        orderId: verification.orderId || null,
        verifiedAt: FieldValue.serverTimestamp()
      });

      tx.set(
        walletRef,
        {
          ...wData,
          coins: nextCoins,
          gems: nextGems,
          vipActive: wData.vipActive || Boolean(sku.vipActive),
          battlePassPremium: wData.battlePassPremium || Boolean(sku.battlePassPremium),
          adsRemoved: wData.adsRemoved || Boolean(sku.adsRemoved),
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );

      if (sku.skinId && userSnap.exists) {
        const uData = userSnap.data();
        const skins = Array.isArray(uData.unlockedSkinIds) ? uData.unlockedSkinIds : [];
        if (!skins.includes(sku.skinId)) {
          tx.update(userRef, {
            unlockedSkinIds: [...skins, sku.skinId].slice(0, 60),
            updatedAt: FieldValue.serverTimestamp()
          });
        }
      }

      if ((sku.coins || 0) > 0 || (sku.gems || 0) > 0) {
        tx.set(txRef, {
          txId: txRef.id,
          uid,
          coinsDelta: sku.coins || 0,
          gemsDelta: sku.gems || 0,
          balanceAfterCoins: nextCoins,
          balanceAfterGems: nextGems,
          reason: 'PLAY_PURCHASE_VERIFIED',
          referenceId: verification.orderId || tokenHash,
          metadata: { productId },
          createdAt: FieldValue.serverTimestamp()
        });
      }

      return {
        status: 'VERIFIED_AND_GRANTED',
        productId,
        orderId: verification.orderId || null,
        coins: nextCoins,
        gems: nextGems,
        vipActive: wData.vipActive || Boolean(sku.vipActive),
        battlePassPremium: wData.battlePassPremium || Boolean(sku.battlePassPremium),
        adsRemoved: wData.adsRemoved || Boolean(sku.adsRemoved)
      };
    });

    return result;
  }
}
