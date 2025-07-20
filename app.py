from flask import Flask, render_template, request, send_from_directory
import pandas as pd
import os
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads'

if not os.path.exists(app.config['UPLOAD_FOLDER']):
    os.makedirs(app.config['UPLOAD_FOLDER'])

# Mapeamento das unidades da planilha para quem coleta e o limite de objetos
REGRAS_COLETA = {
    'ac maua': {'coletor': 'CDD Maua', 'limite': 300},
    'agf franco da rocha': {'coletor': 'CEE Jaguare', 'limite': 1500},
    'agf brigadeiro faria lima': {'coletor': 'CDD São Bernardo do Campo', 'limite': 900},
    'agf maria candida': {'coletor': 'CDD aruja', 'limite': 20},
    # Adicione mais regras aqui conforme necessário
}

def calcular_veiculos(objetos):
    caminhoes = objetos// 1500
    resto = objetos % 1500
    scudos = resto // 400
    resto = resto % 4
    fiorinos = resto
    return {'Caminhao': caminhoes, 'Scudo': scudos, 'Fiorino': fiorinos}

@app.route('/', methods=['GET', 'POST'])
def index():
    resultado = None
    erro = None

    if request.method == 'POST':
        file = request.files.get('file')
        if file and file.filename != '':
            filename = secure_filename(file.filename)
            filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            file.save(filepath)

            try:
                df = pd.read_excel(filepath)

                if 'Unidade' not in df.columns or 'CDL' not in df.columns:
                    erro = 'A planilha deve ter as colunas "Unidade" e "CDL".'
                else:
                    resultado = []
                    for _, row in df.iterrows():
                        unidade = str(row['Unidade']).strip().lower()
                        cdl = int(row['CDL'])
                        veiculos = calcular_veiculos(cdl)

                        # Busca a regra de coleta para a unidade
                        regra = REGRAS_COLETA.get(unidade)
                        if regra:
                            coletor = regra['coletor']
                            limite = regra['limite']
                            if cdl > limite:
                                status = f"{coletor} NÃO CONSEGUE COLETAR (Limite: {limite})"
                            else:
                                status = f"{coletor} CONSEGUE COLETAR"
                        else:
                            coletor = unidade
                            status = f"{coletor} (sem regra definida)"

                        resultado.append({
                            'Unidade': unidade,
                            'CDL': cdl,
                            **veiculos,
                            'Status': status
                        })
            except Exception as e:
                erro = f'Erro ao processar a planilha: {e}'
            finally:
                if os.path.exists(filepath):
                    os.remove(filepath)
        else:
            erro = 'Nenhum arquivo foi selecionado.'

    return render_template('index.html', resultado=resultado, erro=erro)

# Rota para servir arquivos enviados (uploads)
@app.route('/uploads/<filename>')
def uploaded_file(filename):
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)

if __name__ == '__main__':
    app.run(debug=True)