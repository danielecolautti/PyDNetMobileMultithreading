# PydnetTFLite_plus

## Configurazioni per il testing

Tutte le variabili che modificano il comportamento dell'applicazione sono in MainActivity.java

### 1. CPU/GPU

Impostare la variabile GPU uguale a false per far eseguire l'inferenza sulla CPU oppure a true per eseguirla sulla GPU.

### 2. Frame Concorrenti

Indica il numero massimo di elaborazioni concorrenti.

NB: se serve verificare il tempo di inferenza non in parallelo si può settare questa variabile a 1.

NB: un numero troppo elevato in alcuni dispositivi porta ad errori di allocazione di memoria

NB: lavorando sulla CPU, questo valore serve anche per mantenere un buon compromesso tra frame rate e ritardo di 
visualizzazione.

### 3. Campionamento

Settare la variabile enableInferenceFrequency a false per elaborare ogni frame disponibile oppure a true per prendere un frame
ogni inferenceFrequency ms (scartando gli altri). Nel secondo caso il valore inferenceFrequency viene ricalcolato 
dinamicamente basandosi sul tempo medio di inferenza. In entrambi i casi il frame viene processato solo se non ci sono già 
troppi thread (=frameConcorrenti) che eseguono inferenze in esecuzione.
