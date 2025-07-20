from flask import Flask, render_template, request, send_from_directory
import pandas as pd
import os
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads'

if not os.path.exists(app.config['UPLOAD_FOLDER']):
    os.makedirs(app.config['UPLOAD_FOLDER'])

# Regras de coleta: unidade origem -> {coletor, limite_max_objetos}
REGRAS_COLETA = {
    'agf maria candida': {'coletor': 'CDD Arujá', 'limite': 20},
    'ac maua': {'coletor': 'CDD Mauá', 'limite': 300},
    'agf franco da rocha': {'coletor': 'CEE Jaguaré', 'limite': 1500},
    'agf brigadeiro faria lima': {'coletor': 'CDD São Bernardo do Campo', 'limite': 900},
    'agf rua antonio de barros': {'coletor': 'CDD São Paulo', 'limite': 500},
    'agf jardim santa mena': {'coletor': 'CDD Guarulhos', 'limite': 800},
    'agf avenida celso garcia': {'coletor': 'CDD Penha', 'limite': 600},
    'ac ferraz de vasconcelos': {'coletor': 'CDD Ferraz de Vasconcelos', 'limite': 400},
    # Adicione mais se necessário
}

# Converte quantidade de objetos em frota necessária
# 1 Caminhão  = 1.500 objetos
# 1 Scudo     =   400 objetos (resto após caminhões)
# 1 Fiorino   =     1 objeto  (resto após scudos)

def calcular_veiculos(qtd_obj):
    caminhoes = qtd_obj // 1500
    resto = qtd_obj % 1500
    scudos = resto // 400
    resto = resto % 400
    fiorinos = 1 if resto > 0 else 0
    return {
        'Caminhao': caminhoes,
        'Scudo': scudos,
        'Fiorino': fiorinos
    }

@app.route('/', methods=['GET', 'POST'])
def index():
    resultado = None
    erro = None

    if request.method == 'POST':
        file = request.files.get('file')
        if file and file.filename:
            filename = secure_filename(file.filename)
            filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            file.save(filepath)
            try:
                df = pd.read_excel(filepath)

                col_required = ['Objeto', 'Data Prevista de Entrega', 'Situação', 'Nome da Unidade']
                if not set(col_required).issubset(df.columns):
                    erro = f"A planilha deve conter as colunas: {', '.join(col_required)}"
                else:
                    # Normaliza nomes de unidade (lower + strip)
                    df['NomeNorm'] = df['Nome da Unidade'].astype(str).str.lower().str.strip()
                    # Converte data prevista para datetime para agregação (ignora erros)
                    df['Data Prevista de Entrega'] = pd.to_datetime(df['Data Prevista de Entrega'], errors='coerce')

                    # Considera último evento de cada objeto na unidade (o evento mais recente pela data/hora da planilha)
                    # Para simplificar, usaremos o registro mais recente (maior índice) de cada (Objeto, NomeNorm)
                    df_unique = df.drop_duplicates(subset=['Objeto', 'NomeNorm'], keep='last')

                    # Agrupa por unidade
                    grp = df_unique.groupby('NomeNorm').agg(
                        Objetos=('Objeto', 'nunique'),
                        DataMin=('Data Prevista de Entrega', 'min')
                    ).reset_index()

                    resultado = []
                    for _, row in grp.iterrows():
                        unidade_norm = row['NomeNorm']
                        unidade_disp = unidade_norm.title()
                        objetos = int(row['Objetos'])
                        data_prevista = row['DataMin'].strftime('%d/%m/%Y') if pd.notna(row['DataMin']) else '-'

                        veic = calcular_veiculos(objetos)
                        # Define coletor e se consegue coletar
                        regra = REGRAS_COLETA.get(unidade_norm)
                        if regra:
                            coletor = regra['coletor']
                            limite = regra['limite']
                            status = 'CONSEGUE COLETAR' if objetos <= limite else 'NÃO CONSEGUE COLETAR'
                        else:
                            coletor = unidade_disp
                            status = 'Sem regra definida'

                        resultado.append({
                            'Unidade': unidade_disp,
                            'Objetos': objetos,
                            'Data Prevista Mín.': data_prevista,
                            'Coletor': coletor,
                            **veic,
                            'Status': status
                        })

                    # Ordena por quantidade de objetos desc
                    resultado = sorted(resultado, key=lambda x: x['Objetos'], reverse=True)
            except Exception as e:
                erro = f"Erro ao processar a planilha: {e}"
            finally:
                if os.path.exists(filepath):
                    os.remove(filepath)
        else:
            erro = 'Nenhum arquivo selecionado.'

    return render_template('index.html', resultado=resultado, erro=erro)

# Rota para servir uploads
@app.route('/uploads/<filename>')
def uploaded_file(filename):
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)

if __name__ == '__main__':
    app.run(debug=True)
