clc();
clear();
delete(findall(0,'Type','figure'));
I=0:127;
%h=circularGraph(H_AusOpen,'Label',H_AusOpen_names);
H_Wimbledon = importdata("MatrixH_2017-540.txt","\t",0);
H_Wimbledon_names = importdata("MatrixH_2017-540_players.txt");
H_Wimbledon_desc = table2cell(readtable("MatrixH_2017-540_desc.txt",'ReadVariableNames',false,'Delimiter','tab','ReadVariableNames',false));
H_RolGar = importdata("MatrixH_2017-520.txt","\t",0);
H_RolGar_names = importdata("MatrixH_2017-520_players.txt");
H_RolGar_desc = table2cell(readtable("MatrixH_2017-520_desc.txt",'ReadVariableNames',false,'Delimiter','tab','ReadVariableNames',false));
H_UsOpen = importdata("MatrixH_2017-560.txt","\t",0);
H_UsOpen_names = importdata("MatrixH_2017-560_players.txt");
H_UsOpen_desc = table2cell(readtable("MatrixH_2017-560_desc.txt",'ReadVariableNames',false,'Delimiter','tab','ReadVariableNames',false));
H_AusOpen = importdata("MatrixH_2017-580.txt","\t",0);
H_AusOpen_names = importdata("MatrixH_2017-580_players.txt");
H_AusOpen_desc = table2cell(readtable("MatrixH_2017-580_desc.txt",'ReadVariableNames',false,'Delimiter','tab','ReadVariableNames',false));
output = 'Results.csv';



l1=0.35;
u1=0.65;
l2=0.45;
u2=0.55;

adj2gephilab('Gephi/Wimbledon',H_Wimbledon,H_Wimbledon_names,H_Wimbledon_desc);
Wimbledon_G=graph(H_Wimbledon,H_Wimbledon_names);
R_Wimbledon_AVG_degree = round(mean(degree(Wimbledon_G)),2);
Wimbledon_wdeg = sum(H_Wimbledon,2);
R_Wimbledon_AVG_WDegree = round(sum(Wimbledon_wdeg)/128,2);
Wimbledon_deg = [I',degree(Wimbledon_G)];
R_Wimbledon_Hmax=max(H_Wimbledon(:));
R_Wimbledon_max_deg = max(Wimbledon_deg(:,2));
R_Wimbledon_range1 = 0;
R_Wimbledon_range2 = 0;
R_Wimbledon_QLL =0;
for i=1:128
    if (Wimbledon_deg(i,2)>R_Wimbledon_max_deg*l1 && Wimbledon_deg(i,2)<R_Wimbledon_max_deg*u1)
        R_Wimbledon_range1 = R_Wimbledon_range1 +1;
    end
    if (Wimbledon_deg(i,2)>R_Wimbledon_max_deg*l2 && Wimbledon_deg(i,2)<R_Wimbledon_max_deg*u2)
        R_Wimbledon_range2 = R_Wimbledon_range2 +1;
    end
    if (Wimbledon_deg(i,2) == 0)
        R_Wimbledon_QLL=R_Wimbledon_QLL+1;
    end
end
figure
histogram(Wimbledon_deg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','blue','FaceColor','blue');
hold on
histogram(Wimbledon_wdeg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','red','FaceColor','red');
legend('Degree','Weighted degree')
saveas(gcf,'Images/Wimbledon_Distrubition.png')
figure
probplot([Wimbledon_deg(:,end) Wimbledon_wdeg(:,end)]);
legend('Degree','Weighted degree')
saveas(gcf,'Images/Wimbledon_Fit.png')
figure('units','normalized','outerposition',[0 0 0.5 0.8]);
cg_Wimbledon = circularGraph(H_Wimbledon,'Label',H_Wimbledon_names);
zoom
set(gcf,'PaperPositionMode','auto');
print('Images/Wimbledon_Circular.png','-dpng','-r300','-noui');


adj2gephilab('Gephi/RolGar',H_RolGar,H_RolGar_names,H_RolGar_desc);
RolGar_G=graph(H_RolGar,H_RolGar_names);
R_RolGar_AVG_degree = round(mean(degree(RolGar_G)),2);
RolGar_wdeg = sum(H_RolGar,2);
R_RolGar_AVG_WDegree = round(sum(RolGar_wdeg)/128,2);
RolGar_deg = [I',degree(RolGar_G)];
R_RolGar_Hmax=max(H_RolGar(:));
R_RolGar_max_deg = max(RolGar_deg(:,2));
R_RolGar_range1 = 0;
R_RolGar_range2 = 0;
R_RolGar_QLL =0;
for i=1:128
    if (RolGar_deg(i,2)>R_RolGar_max_deg*l1 && RolGar_deg(i,2)<R_RolGar_max_deg*u1)
        R_RolGar_range1 = R_RolGar_range1 +1;
    end
    if (RolGar_deg(i,2)>R_RolGar_max_deg*l2 && RolGar_deg(i,2)<R_RolGar_max_deg*u2)
        R_RolGar_range2 = R_RolGar_range2 +1;
    end
    if (RolGar_deg(i,2) == 0)
        R_RolGar_QLL=R_RolGar_QLL+1;
    end
end
figure
histogram(RolGar_deg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','blue','FaceColor','blue');
hold on
histogram(RolGar_wdeg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','red','FaceColor','red');
legend('Degree','Weighted degree')
saveas(gcf,'Images/RolGar_Distrubition.png')
figure
probplot([RolGar_deg(:,end) RolGar_wdeg(:,end)]);
legend('Degree','Weighted degree')
saveas(gcf,'Images/RolGar_Fit.png')
figure('units','normalized','outerposition',[0 0 0.5 0.8]);
cg_RolGar = circularGraph(H_RolGar,'Label',H_RolGar_names);
zoom
set(gcf,'PaperPositionMode','auto');
print('Images/RolGar_Circular.png','-dpng','-r300','-noui');


adj2gephilab('Gephi/UsOpen',H_UsOpen,H_UsOpen_names,H_UsOpen_desc);
UsOpen_G=graph(H_UsOpen,H_UsOpen_names);
R_UsOpen_AVG_degree = round(mean(degree(UsOpen_G)),2);
UsOpen_wdeg = sum(H_UsOpen,2);
R_UsOpen_AVG_WDegree = round(sum(UsOpen_wdeg)/128,2);
UsOpen_deg = [I',degree(UsOpen_G)];
R_UsOpen_Hmax=max(H_UsOpen(:));
R_UsOpen_max_deg = max(UsOpen_deg(:,2));
R_UsOpen_range1 = 0;
R_UsOpen_range2 = 0;
R_UsOpen_QLL =0;
for i=1:128
    if (UsOpen_deg(i,2)>R_UsOpen_max_deg*l1 && UsOpen_deg(i,2)<R_UsOpen_max_deg*u1)
        R_UsOpen_range1 = R_UsOpen_range1 +1;
    end
    if (UsOpen_deg(i,2)>R_UsOpen_max_deg*l2 && UsOpen_deg(i,2)<R_UsOpen_max_deg*u2)
        R_UsOpen_range2 = R_UsOpen_range2 +1;
    end
    if (UsOpen_deg(i,2) == 0)
        R_UsOpen_QLL=R_UsOpen_QLL+1;
    end
end
figure
histogram(UsOpen_deg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','blue','FaceColor','blue');
hold on
histogram(UsOpen_wdeg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','red','FaceColor','red');
legend('Degree','Weighted degree')
saveas(gcf,'Images/UsOpen_Distrubition.png')
figure
probplot([UsOpen_deg(:,end) UsOpen_wdeg(:,end)]);
legend('Degree','Weighted degree')
saveas(gcf,'Images/UsOpen_Fit.png')
figure('units','normalized','outerposition',[0 0 0.5 0.8]);
cg_UsOpen = circularGraph(H_UsOpen,'Label',H_UsOpen_names);
zoom
set(gcf,'PaperPositionMode','auto');
print('Images/UsOpen_Circular.png','-dpng','-r300','-noui');



adj2gephilab('Gephi/AusOpen',H_AusOpen,H_AusOpen_names,H_AusOpen_desc);
AusOpen_G=graph(H_AusOpen,H_AusOpen_names);
R_AusOpen_AVG_degree = round(mean(degree(AusOpen_G)),2);
AusOpen_wdeg = sum(H_AusOpen,2);
R_AusOpen_AVG_WDegree = round(sum(AusOpen_wdeg)/128,2);
AusOpen_deg = [I',degree(AusOpen_G)];
R_AusOpen_Hmax=max(H_AusOpen(:));
R_AusOpen_max_deg = max(AusOpen_deg(:,2));
R_AusOpen_range1 = 0;
R_AusOpen_range2 = 0;
R_AusOpen_QLL =0;
for i=1:128
    if (AusOpen_deg(i,2)>R_AusOpen_max_deg*l1 && AusOpen_deg(i,2)<R_AusOpen_max_deg*u1)
        R_AusOpen_range1 = R_AusOpen_range1 +1;
    end
    if (AusOpen_deg(i,2)>R_AusOpen_max_deg*l2 && AusOpen_deg(i,2)<R_AusOpen_max_deg*u2)
        R_AusOpen_range2 = R_AusOpen_range2 +1;
    end
    if (AusOpen_deg(i,2) == 0)
        R_AusOpen_QLL=R_AusOpen_QLL+1;
    end
end
figure
histogram(AusOpen_deg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','blue','FaceColor','blue');
hold on
histogram(AusOpen_wdeg(:,end),'BinWidth',3,'FaceAlpha',0.5,'EdgeAlpha',0.5,'EdgeColor','red','FaceColor','red');
legend('Degree','Weighted degree')
saveas(gcf,'Images/AusOpen_Distrubition.png')
figure
probplot([AusOpen_deg(:,end) AusOpen_wdeg(:,end)]);
legend('Degree','Weighted degree')
saveas(gcf,'Images/AusOpen_Fit.png')
figure('units','normalized','outerposition',[0 0 0.5 0.8]);
cg_AusOpen = circularGraph(H_AusOpen,'Label',H_AusOpen_names);
zoom
set(gcf,'PaperPositionMode','auto');
print('Images/AusOpen_Circular.png','-dpng','-r300','-noui');

fid = fopen(output,'w'); 
format bank
fprintf(fid,'#,RG,WI,US,AUS\n');
fprintf(fid,'Average degree,%.2f,%.2f,%.2f,%.2f\n',R_RolGar_AVG_degree,R_Wimbledon_AVG_degree,R_UsOpen_AVG_degree,R_AusOpen_AVG_degree);
fprintf(fid,'Average weighted degree,%.2f,%.2f,%.2f,%.2f\n',R_RolGar_AVG_WDegree,R_Wimbledon_AVG_WDegree,R_UsOpen_AVG_WDegree,R_AusOpen_AVG_WDegree);
fprintf(fid,'Number of Q and LL,%.0f,%.0f,%.0f,%.0f\n',R_RolGar_QLL,R_Wimbledon_QLL,R_UsOpen_QLL,R_AusOpen_QLL);
fprintf(fid,'Max h in H,%.2f,%.2f,%.2f,%.2f\n',R_RolGar_Hmax,R_Wimbledon_Hmax,R_UsOpen_Hmax,R_AusOpen_Hmax);
fprintf(fid,'Max degree MD,%.0f,%.0f,%.0f,%.0f\n',R_RolGar_max_deg,R_Wimbledon_max_deg,R_UsOpen_max_deg,R_AusOpen_max_deg);
fprintf(fid,'Max weighted degree,%.2f,%.2f,%.2f,%.2f\n',max(RolGar_wdeg),max(Wimbledon_wdeg),max(UsOpen_wdeg),max(AusOpen_wdeg));
fprintf(fid,'"Nodes with degree in [0.35MD,0.65MD]",%.2f,%.2f,%.2f,%.2f\n',R_RolGar_range1,R_Wimbledon_range1,R_UsOpen_range1,R_AusOpen_range1);
fprintf(fid,'"Nodes with degree in [0.45MD,0.55MD]",%.2f,%.2f,%.2f,%.2f\n',R_RolGar_range2,R_Wimbledon_range2,R_UsOpen_range2,R_AusOpen_range2);

fclose(fid);