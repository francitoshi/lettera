// Esperamos a que el DOM esté completamente cargado para asegurarnos
// de que todos los elementos HTML existen.
document.addEventListener('DOMContentLoaded', () => {

    // Obtenemos referencias a los elementos del DOM
    const sourceEl = document.getElementById('source');
    const resultEl = document.getElementById('result');
    const zipBtn = document.getElementById('zipBtn');
    const unzipBtn = document.getElementById('unzipBtn');
    const exitBtn = document.getElementById('exitBtn');

    // 'javaBridge' es el objeto que inyectamos desde Java.
    // Lo podemos usar directamente como si fuera un objeto de JavaScript.
    
    // Evento para el botón "Zip"
    zipBtn.addEventListener('click', () => {
        const sourceText = sourceEl.value;
        if (sourceText) {
            // Llamamos al método zip() de nuestra clase JavaBridge
            const zippedText = javaBridge.zip(sourceText);
            resultEl.value = zippedText;
        }
    });

    // Evento para el botón "Unzip"
    unzipBtn.addEventListener('click', () => {
        const sourceText = sourceEl.value;
        if (sourceText) {
            // Llamamos al método unzip() de nuestra clase JavaBridge
            const unzippedText = javaBridge.unzip(sourceText);
            resultEl.value = unzippedText;
        }
    });

    // Evento para el botón "Exit"
    exitBtn.addEventListener('click', () => {
        // Llamamos al método exit() de nuestra clase JavaBridge
        javaBridge.exit();
    });

});