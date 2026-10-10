/* Payment details for the /support/ page. THE ONLY FILE TO EDIT. See web/README.md, "Support page".

   Rules
   - Every value that is still "CHANGE_ME" (or empty) counts as NOT FILLED IN.
   - A method is shown only when `enabled: true` AND all of its required fields are filled in.
     Crypto: each address is shown only when its own `address` is filled in.
   - `qr` is the path of a static image under web/public/assets/support/ (PNG, SVG or WebP).
     Leave it "CHANGE_ME" to show no QR. Never point it at another website.
   - Preview: open /support/?preview=1 (or any localhost URL) to see every method, including the
     ones that are disabled or not filled in, marked "Not configured". Visitors never see them.
   - Never commit made-up numbers. Only your real, checked details, or CHANGE_ME. */
window.TSIPTV_SUPPORT = {
  // Optional transfer note shown with bank and wallet methods (ASCII, no accents: some banks strip them).
  transferNote: "TSIPTV ung ho",

  methods: [
    // ---- Requested methods --------------------------------------------------------------------

    // Bank transfer by VietQR / Napas 247. Required: bankName, accountNumber, accountName.
    {
      id: "vietqr",
      enabled: true,
      bankName: "CHANGE_ME",          // e.g. the bank's short name as printed in its app
      accountNumber: "CHANGE_ME",
      accountName: "CHANGE_ME",       // account holder, CAPITALS WITHOUT ACCENTS as the bank shows it
      branch: "",                     // optional
      qr: "CHANGE_ME"                 // e.g. "/assets/support/vietqr.png"
    },

    // MoMo. Required: phone OR link (at least one).
    {
      id: "momo",
      enabled: true,
      phone: "CHANGE_ME",             // the MoMo phone number shown to donors
      accountName: "CHANGE_ME",       // name shown in MoMo (optional)
      link: "CHANGE_ME",              // your MoMo receive link (https://…), opens the app or the web
      appLink: "momo://",             // "Open app" button; falls back to `link`
      qr: "CHANGE_ME"                 // e.g. "/assets/support/momo.png"
    },

    // Zypage creator page. Required: url.
    {
      id: "zypage",
      enabled: true,
      url: "CHANGE_ME"                // your Zypage page URL (https://…)
    },

    // Crypto. One entry per asset + network. Each address is shown only when filled in.
    {
      id: "crypto",
      enabled: true,
      addresses: [
        { asset: "USDT", network: "TRON (TRC20)", address: "CHANGE_ME", memo: "", qr: "CHANGE_ME" },
        { asset: "USDT", network: "BNB Smart Chain (BEP20)", address: "CHANGE_ME", memo: "", qr: "CHANGE_ME" },
        { asset: "BTC", network: "Bitcoin", address: "CHANGE_ME", memo: "", qr: "CHANGE_ME" },
        { asset: "ETH", network: "Ethereum (ERC20)", address: "CHANGE_ME", memo: "", qr: "CHANGE_ME" }
      ]
    },

    // ---- Suggested methods: disabled until you add your details ----------------------------------

    // ZaloPay. Required: phone OR link.
    { id: "zalopay", enabled: false, phone: "CHANGE_ME", accountName: "CHANGE_ME", link: "CHANGE_ME", appLink: "zalopay://", qr: "CHANGE_ME" },

    // VNPay QR (a merchant or personal VNPAY-QR image from your bank / VNPay). Required: qr.
    { id: "vnpay", enabled: false, accountName: "CHANGE_ME", qr: "CHANGE_ME" },

    // ShopeePay. Required: phone OR qr.
    { id: "shopeepay", enabled: false, phone: "CHANGE_ME", accountName: "CHANGE_ME", qr: "CHANGE_ME" },

    // PayPal.me. Required: url (https://paypal.me/…).
    { id: "paypal", enabled: false, url: "CHANGE_ME" },

    // Ko-fi. Required: url (https://ko-fi.com/…).
    { id: "kofi", enabled: false, url: "CHANGE_ME" },

    // Buy Me a Coffee. Required: url (https://buymeacoffee.com/…).
    { id: "bmac", enabled: false, url: "CHANGE_ME" },

    // GitHub Sponsors. Required: url (https://github.com/sponsors/…).
    { id: "github", enabled: false, url: "CHANGE_ME" },

    // Stripe Payment Link. Required: url (https://buy.stripe.com/…).
    { id: "stripe", enabled: false, url: "CHANGE_ME" },

    // Binance Pay. Required: payId.
    { id: "binance", enabled: false, payId: "CHANGE_ME", accountName: "CHANGE_ME", qr: "CHANGE_ME" }
  ]
};
