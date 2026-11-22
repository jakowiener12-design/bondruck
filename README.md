# Bondruck

Android App für Cardano Kebapizza: Liest WhatsApp Bestellungen mit, macht daraus einen sauberen Bon und druckt ihn per LAN auf den Sunmi Bondrucker (ESC/POS, Port 9100).

- Sammelt alle Nachrichten eines Kunden zu einer Bestellung
- Artikel untereinander, darunter TEL und ADRESSE, Originaltext klein zur Kontrolle
- Autodruck nach einstellbarer Ruhezeit, wenn Artikel und Adresse erkannt wurden
- Benachrichtigung je Kunde mit Drucken / Nachdrucken / Erledigt
- Freier Text Editor, Teilen aus jeder App an Bondruck
- Schriftgröße einstellbar

Jeder Push auf `main` baut die APK automatisch (GitHub Actions) und legt sie unter Releases ab.
