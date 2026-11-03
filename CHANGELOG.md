# Changelog

Tüm önemli değişiklikler bu dosyada belgelenmektedir.

## [1.1] - 2026-11-02

### Eklenenler & İyileştirmeler
- **Tema Seçici Sıralaması ve Senkronizasyon:** Ayarlar ve yan menü tema seçicilerinde kanonik sıra (Beyaz Cam, Siyah Cam, Beyaz, Siyah) uygulandı; tema değişiminde dock, kutucuk, menü, panel ve saat anında senkronize hale getirildi.
- **Dock ve Saat Kontrastı:** Beyaz (bow) ve Siyah (amoled) temalarında okunabilirliği artıran yüksek kontrastlı mürekkep yazılar, beyaz kadran halkaları ve optimize dock arka planları sağlandı.
- **Hakkında (About) Sayfası:** Geliştirici bilgisi tam proje kimliğini yansıtacak şekilde `made by crius (owergungor)` olarak güncellendi.
- **Yedekleme (Backup) Entegrasyonu:** Yedekleme ve geri yükleme işlemleri, SHA-256 doğrulama ve bütünlük kontrolleri korunarak Hakkında sekmesine taşındı; bağımsız yedekleme sekmesi sadeleştirildi.
- **Sistem Sekmesi & Erişilebilirlik:** Canlı erişilebilirlik servis denetimi, Home tuşu yönlendirme optimizasyonu (`ensureHomeService`) ve arka plan kaynak tüketimi iyileştirmeleri eklendi.
- **Türkçe Yerelleştirme:** 497 çeviri anahtarını kapsayan kapsamlı Türkçe dil desteği ve gramer düzeltmeleri tamamlandı.
- **Android TV D-Pad & Düşük RAM Uyumu:** Android TV kumanda odak (focus) geçişleri, Android 9 (API 28) ve 1GB RAM cihazlar için bellek ayak izi optimizasyonları korundu ve pekiştirildi.

---

## [1.0] - 2026-11-02

- Alfredo ilk kararlı sürüm yayını.
- Glassio temelinden Alfredo yeniden markalaması ve paket ayrıştırması.
- Production release anahtar altyapısı ve otomatik imzalama.
- Güvenli ZIP ve SHA-256 tabanlı yedekleme/geri yükleme altyapısı.
- Background-first hızlı başlangıç mimarisi.
